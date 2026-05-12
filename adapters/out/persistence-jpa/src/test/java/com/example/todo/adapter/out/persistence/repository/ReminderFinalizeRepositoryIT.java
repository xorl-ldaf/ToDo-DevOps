package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderFinalizeRepositoryIT extends AbstractReminderPersistenceRepositoryIT {

    @Test
    void markDeliveredShouldSucceedOnlyWithMatchingProcessingOwner() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 0));

        boolean wrongOwnerResult = adapter.markDelivered(new ReminderId(reminderId), "worker-2", NOW);
        boolean matchingOwnerResult = adapter.markDelivered(new ReminderId(reminderId), "worker-1", NOW);

        assertThat(wrongOwnerResult).isFalse();
        assertThat(matchingOwnerResult).isTrue();
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(stored.getProcessingOwner()).isNull();
        assertThat(stored.getProcessingStartedAt()).isNull();
        assertThat(stored.getDeliveredAt()).isEqualTo(NOW);
        assertThat(stored.getDeliveryAttempts()).isEqualTo(1);
        assertThat(stored.getLastFailureReason()).isNull();
    }

    @Test
    void markDeliveredShouldReturnFalseForWrongOwner() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 0));

        boolean result = adapter.markDelivered(new ReminderId(reminderId), "worker-2", NOW);

        assertThat(result).isFalse();
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.PROCESSING);
        assertThat(stored.getProcessingOwner()).isEqualTo("worker-1");
        assertThat(stored.getDeliveredAt()).isNull();
    }

    @Test
    void rescheduleShouldMoveProcessingReminderToScheduledWithNextAttemptAt() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        Instant nextAttemptAt = NOW.plusSeconds(60);
        seedTask(taskId);
        adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 1));

        boolean result = adapter.reschedule(
                new ReminderId(reminderId),
                "worker-1",
                NOW,
                nextAttemptAt,
                "telegram timeout"
        );

        assertThat(result).isTrue();
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
        assertThat(stored.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(stored.getProcessingOwner()).isNull();
        assertThat(stored.getProcessingStartedAt()).isNull();
        assertThat(stored.getDeliveryAttempts()).isEqualTo(2);
        assertThat(stored.getLastFailureReason()).isEqualTo("telegram timeout");
    }

    @Test
    void markFailedShouldMoveProcessingReminderToFailed() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 1));

        boolean result = adapter.markFailed(
                new ReminderId(reminderId),
                "worker-1",
                NOW,
                "recipient has no telegram chat id"
        );

        assertThat(result).isTrue();
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.FAILED);
        assertThat(stored.getProcessingOwner()).isNull();
        assertThat(stored.getProcessingStartedAt()).isNull();
        assertThat(stored.getDeliveryAttempts()).isEqualTo(2);
        assertThat(stored.getLastFailureReason()).isEqualTo("recipient has no telegram chat id");
    }
}
