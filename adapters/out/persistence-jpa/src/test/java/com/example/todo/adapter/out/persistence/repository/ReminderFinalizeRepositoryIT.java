package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.application.policy.ReminderLifecyclePolicy;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderFinalizeRepositoryIT extends AbstractReminderPersistenceRepositoryIT {
    private final ReminderLifecyclePolicy reminderLifecyclePolicy = new ReminderLifecyclePolicy();

    @Test
    void finalizeDeliveryShouldSucceedOnlyWithMatchingProcessingOwner() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        Reminder processingReminder = adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 0));
        Reminder deliveredReminder = reminderLifecyclePolicy.markDelivered(processingReminder, NOW);

        boolean wrongOwnerResult = adapter.finalizeDelivery(deliveredReminder, "worker-2");
        boolean matchingOwnerResult = adapter.finalizeDelivery(deliveredReminder, "worker-1");

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
    void finalizeDeliveryShouldReturnFalseForWrongOwner() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        Reminder processingReminder = adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 0));
        Reminder deliveredReminder = reminderLifecyclePolicy.markDelivered(processingReminder, NOW);

        boolean result = adapter.finalizeDelivery(deliveredReminder, "worker-2");

        assertThat(result).isFalse();
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.PROCESSING);
        assertThat(stored.getProcessingOwner()).isEqualTo("worker-1");
        assertThat(stored.getDeliveredAt()).isNull();
    }

    @Test
    void finalizeDeliveryShouldStoreRescheduledReminderState() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        Instant nextAttemptAt = NOW.plusSeconds(60);
        seedTask(taskId);
        Reminder processingReminder = adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 1));
        Reminder rescheduledReminder = reminderLifecyclePolicy.reschedule(
                processingReminder,
                NOW,
                nextAttemptAt,
                "telegram timeout"
        );

        boolean result = adapter.finalizeDelivery(rescheduledReminder, "worker-1");

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
    void finalizeDeliveryShouldStoreFailedReminderState() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        Reminder processingReminder = adapter.save(processingReminder(reminderId, taskId, "worker-1", NOW.minusSeconds(60), 1));
        Reminder failedReminder = reminderLifecyclePolicy.markFailed(
                processingReminder,
                NOW,
                "recipient has no telegram chat id"
        );

        boolean result = adapter.finalizeDelivery(failedReminder, "worker-1");

        assertThat(result).isTrue();
        ReminderJpaEntity stored = requireReminder(reminderId);
        assertThat(stored.getStatus()).isEqualTo(ReminderStatus.FAILED);
        assertThat(stored.getProcessingOwner()).isNull();
        assertThat(stored.getProcessingStartedAt()).isNull();
        assertThat(stored.getDeliveryAttempts()).isEqualTo(2);
        assertThat(stored.getLastFailureReason()).isEqualTo("recipient has no telegram chat id");
    }
}
