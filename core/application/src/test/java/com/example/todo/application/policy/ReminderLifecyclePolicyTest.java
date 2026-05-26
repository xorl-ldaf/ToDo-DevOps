package com.example.todo.application.policy;

import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderLifecyclePolicyTest {
    private static final Instant NOW = Instant.parse("2026-04-19T10:00:00Z");

    private final ReminderLifecyclePolicy policy = new ReminderLifecyclePolicy();

    @Test
    void isDueAtShouldUseNextAttemptAtForScheduledReminder() {
        Reminder reminder = scheduledReminder(NOW.plusSeconds(60));

        assertFalse(policy.isDueAt(reminder, NOW));
        assertTrue(policy.isDueAt(reminder, NOW.plusSeconds(60)));
    }

    @Test
    void markProcessingShouldMoveReminderToProcessing() {
        Instant claimedAt = NOW.plusSeconds(60);

        Reminder reminder = policy.markProcessing(scheduledReminder(claimedAt), "worker-a", claimedAt);

        assertEquals(ReminderStatus.PROCESSING, reminder.getStatus());
        assertEquals(claimedAt, reminder.getProcessingStartedAt());
        assertEquals("worker-a", reminder.getProcessingOwner());
        assertEquals(claimedAt, reminder.getUpdatedAt());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void markProcessingShouldRejectBlankProcessorId() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.markProcessing(scheduledReminder(NOW.plusSeconds(60)), " ", NOW.plusSeconds(60))
        );

        assertEquals("processorId must not be blank", exception.getMessage());
    }

    @Test
    void markDeliveredShouldMoveReminderFromProcessingToDelivered() {
        Reminder processingReminder = processingReminder(0);

        Reminder deliveredReminder = policy.markDelivered(processingReminder, NOW.plusSeconds(61));

        assertEquals(ReminderStatus.DELIVERED, deliveredReminder.getStatus());
        assertEquals(NOW.plusSeconds(61), deliveredReminder.getDeliveredAt());
        assertEquals(1, deliveredReminder.getDeliveryAttempts());
        assertNull(deliveredReminder.getProcessingStartedAt());
        assertNull(deliveredReminder.getProcessingOwner());
        assertNull(deliveredReminder.getLastFailureReason());
    }

    @Test
    void rescheduleShouldMoveReminderBackToScheduledAndTrackFailureReason() {
        Reminder processingReminder = processingReminder(1);
        Instant nextAttemptAt = NOW.plusSeconds(120);

        Reminder rescheduledReminder = policy.reschedule(
                processingReminder,
                NOW.plusSeconds(61),
                nextAttemptAt,
                "transient notification failure"
        );

        assertEquals(ReminderStatus.SCHEDULED, rescheduledReminder.getStatus());
        assertEquals(nextAttemptAt, rescheduledReminder.getNextAttemptAt());
        assertEquals(2, rescheduledReminder.getDeliveryAttempts());
        assertEquals("transient notification failure", rescheduledReminder.getLastFailureReason());
        assertNull(rescheduledReminder.getProcessingStartedAt());
        assertNull(rescheduledReminder.getProcessingOwner());
        assertNull(rescheduledReminder.getDeliveredAt());
    }

    @Test
    void rescheduleShouldRejectNullNextAttemptAt() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.reschedule(
                        processingReminder(0),
                        NOW.plusSeconds(61),
                        null,
                        "transient notification failure"
                )
        );

        assertEquals("nextAttemptAt must not be null", exception.getMessage());
    }

    @Test
    void markFailedShouldMoveReminderToFailed() {
        Reminder failedReminder = policy.markFailed(
                processingReminder(1),
                NOW.plusSeconds(61),
                "recipient has no telegram chat"
        );

        assertEquals(ReminderStatus.FAILED, failedReminder.getStatus());
        assertEquals(2, failedReminder.getDeliveryAttempts());
        assertEquals("recipient has no telegram chat", failedReminder.getLastFailureReason());
        assertNull(failedReminder.getProcessingStartedAt());
        assertNull(failedReminder.getProcessingOwner());
        assertNull(failedReminder.getDeliveredAt());
    }

    @Test
    void markDeliveredShouldRejectIllegalTransitionFromScheduled() {
        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> policy.markDelivered(scheduledReminder(NOW.plusSeconds(300)), NOW.plusSeconds(60))
        );

        assertEquals("reminder cannot be marked as delivered from status: SCHEDULED", exception.getMessage());
    }

    @Test
    void transitionShouldRejectTimestampsMovingBackwards() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.markFailed(processingReminder(0), NOW.plusSeconds(59), "transient notification failure")
        );

        assertEquals("updatedAt must not move backwards", exception.getMessage());
    }

    private Reminder scheduledReminder(Instant nextAttemptAt) {
        return new Reminder(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                TaskId.newId(),
                NOW.plusSeconds(60),
                ReminderStatus.SCHEDULED,
                NOW,
                NOW,
                nextAttemptAt,
                null,
                null,
                null,
                0,
                null
        );
    }

    private Reminder processingReminder(int deliveryAttempts) {
        Instant processingStartedAt = NOW.plusSeconds(60);
        return new Reminder(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                TaskId.newId(),
                NOW.plusSeconds(60),
                ReminderStatus.PROCESSING,
                NOW,
                processingStartedAt,
                NOW.plusSeconds(60),
                processingStartedAt,
                "worker-a",
                null,
                deliveryAttempts,
                null
        );
    }
}
