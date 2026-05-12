package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderClaimRepositoryIT extends AbstractReminderPersistenceRepositoryIT {

    @Test
    void saveAndLoadReminderShouldPreserveAllFields() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        Reminder reminder = processingReminder(
                reminderId,
                taskId,
                "worker-1",
                NOW.minusSeconds(60),
                2
        );

        adapter.save(reminder);

        List<Reminder> loaded = adapter.loadByTaskId(new TaskId(taskId));
        assertThat(loaded).singleElement().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(reminder.getId());
            assertThat(saved.getTaskId()).isEqualTo(reminder.getTaskId());
            assertThat(saved.getRemindAt()).isEqualTo(reminder.getRemindAt());
            assertThat(saved.getStatus()).isEqualTo(reminder.getStatus());
            assertThat(saved.getCreatedAt()).isEqualTo(reminder.getCreatedAt());
            assertThat(saved.getUpdatedAt()).isEqualTo(reminder.getUpdatedAt());
            assertThat(saved.getNextAttemptAt()).isEqualTo(reminder.getNextAttemptAt());
            assertThat(saved.getProcessingStartedAt()).isEqualTo(reminder.getProcessingStartedAt());
            assertThat(saved.getProcessingOwner()).isEqualTo(reminder.getProcessingOwner());
            assertThat(saved.getDeliveredAt()).isEqualTo(reminder.getDeliveredAt());
            assertThat(saved.getDeliveryAttempts()).isEqualTo(reminder.getDeliveryAttempts());
            assertThat(saved.getLastFailureReason()).isEqualTo(reminder.getLastFailureReason());
        });
    }

    @Test
    void claimDueRemindersShouldClaimOnlyScheduledDueReminders() {
        UUID taskId = UUID.randomUUID();
        UUID dueReminderId = UUID.randomUUID();
        UUID futureReminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(scheduledReminder(dueReminderId, taskId, NOW.minusSeconds(1)));
        adapter.save(scheduledReminder(futureReminderId, taskId, NOW.plusSeconds(60)));

        List<Reminder> claimed = adapter.claimDueReminders(NOW, "worker-1", Duration.ofSeconds(30), 10);

        assertThat(claimed).extracting(reminder -> reminder.getId().value()).containsExactly(dueReminderId);
        assertThat(claimed).singleElement().satisfies(reminder -> {
            assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PROCESSING);
            assertThat(reminder.getProcessingOwner()).isEqualTo("worker-1");
            assertThat(reminder.getProcessingStartedAt()).isEqualTo(NOW);
        });
        assertThat(requireReminder(futureReminderId).getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
    }

    @Test
    void claimDueRemindersShouldNotClaimFutureReminders() {
        UUID taskId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(scheduledReminder(UUID.randomUUID(), taskId, NOW.plusSeconds(60)));

        List<Reminder> claimed = adapter.claimDueReminders(NOW, "worker-1", Duration.ofSeconds(30), 10);

        assertThat(claimed).isEmpty();
    }

    @Test
    void concurrentClaimCallsShouldNotReturnTheSameReminder() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(scheduledReminder(reminderId, taskId, NOW.minusSeconds(1)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Callable<List<Reminder>> firstClaim = () -> claimAfterBarrier(barrier, "worker-1");
            Callable<List<Reminder>> secondClaim = () -> claimAfterBarrier(barrier, "worker-2");

            Future<List<Reminder>> first = executor.submit(firstClaim);
            Future<List<Reminder>> second = executor.submit(secondClaim);

            List<Reminder> allClaimed = Stream.concat(first.get().stream(), second.get().stream()).toList();
            assertThat(allClaimed).hasSize(1);
            assertThat(allClaimed).extracting(reminder -> reminder.getId().value()).containsExactly(reminderId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleProcessingReminderShouldBeReclaimedAfterTimeout() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(processingReminder(reminderId, taskId, "old-worker", NOW.minusSeconds(120), 1));

        List<Reminder> claimed = adapter.claimDueReminders(NOW, "new-worker", Duration.ofSeconds(30), 10);

        assertThat(claimed).extracting(reminder -> reminder.getId().value()).containsExactly(reminderId);
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.PROCESSING);
        assertThat(stored.getProcessingOwner()).isEqualTo("new-worker");
        assertThat(stored.getProcessingStartedAt()).isEqualTo(NOW);
    }

    @Test
    void nonStaleProcessingReminderShouldNotBeReclaimed() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(processingReminder(reminderId, taskId, "old-worker", NOW.minusSeconds(5), 1));

        List<Reminder> claimed = adapter.claimDueReminders(NOW, "new-worker", Duration.ofSeconds(30), 10);

        assertThat(claimed).isEmpty();
        assertThat(requireReminder(reminderId).getProcessingOwner()).isEqualTo("old-worker");
    }

    private List<Reminder> claimAfterBarrier(CyclicBarrier barrier, String workerId) throws Exception {
        barrier.await();
        return adapter.claimDueReminders(NOW, workerId, Duration.ofSeconds(30), 1);
    }
}
