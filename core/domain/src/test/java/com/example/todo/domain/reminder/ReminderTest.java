package com.example.todo.domain.reminder;

import com.example.todo.domain.shared.exception.DomainValidationException;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderTest {

    @Test
    void scheduleShouldCreateScheduledReminderWithConsistentTimestamps() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Instant remindAt = Instant.parse("2026-04-19T11:00:00Z");

        Reminder reminder = Reminder.schedule(TaskId.newId(), remindAt, now);

        assertEquals(ReminderStatus.SCHEDULED, reminder.getStatus());
        assertEquals(remindAt, reminder.getRemindAt());
        assertEquals(remindAt, reminder.getNextAttemptAt());
        assertEquals(now, reminder.getCreatedAt());
        assertEquals(now, reminder.getUpdatedAt());
        assertEquals(0, reminder.getDeliveryAttempts());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void scheduleShouldRejectReminderTimeInThePast() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Instant remindAt = Instant.parse("2026-04-19T09:59:59Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.schedule(TaskId.newId(), remindAt, now)
        );

        assertEquals("remindAt must not be in the past", exception.getMessage());
    }

    @Test
    void scheduleShouldRejectNullTaskId() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.schedule(null, now.plusSeconds(60), now)
        );

        assertEquals("taskId must not be null", exception.getMessage());
    }

    @Test
    void scheduleShouldRejectNullReminderTime() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.schedule(TaskId.newId(), null, now)
        );

        assertEquals("remindAt must not be null", exception.getMessage());
    }

    @Test
    void scheduleShouldRejectNullCurrentTime() {
        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.schedule(TaskId.newId(), Instant.parse("2026-04-19T10:00:00Z"), null)
        );

        assertEquals("now must not be null", exception.getMessage());
    }

    @Test
    void restoreShouldRejectInvalidProcessingState() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.restore(
                        ReminderId.newId(),
                        TaskId.newId(),
                        createdAt.plusSeconds(300),
                        ReminderStatus.PROCESSING,
                        createdAt,
                        createdAt.plusSeconds(1),
                        createdAt.plusSeconds(300),
                        null,
                        "worker-a",
                        null,
                        0,
                        null
                )
        );

        assertEquals("processingStartedAt must be set for PROCESSING reminders", exception.getMessage());
    }

    @Test
    void restoreShouldRejectDeliveredReminderWithoutDeliveredAt() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.restore(
                        ReminderId.newId(),
                        TaskId.newId(),
                        createdAt.plusSeconds(300),
                        ReminderStatus.DELIVERED,
                        createdAt,
                        createdAt.plusSeconds(60),
                        createdAt.plusSeconds(300),
                        null,
                        null,
                        null,
                        1,
                        null
                )
        );

        assertEquals("deliveredAt must be set for DELIVERED reminders", exception.getMessage());
    }

    @Test
    void restoreShouldRejectDeliveredAtOutsideDeliveredStatus() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.restore(
                        ReminderId.newId(),
                        TaskId.newId(),
                        createdAt.plusSeconds(300),
                        ReminderStatus.SCHEDULED,
                        createdAt,
                        createdAt,
                        createdAt.plusSeconds(300),
                        null,
                        null,
                        createdAt.plusSeconds(60),
                        0,
                        null
                )
        );

        assertEquals("deliveredAt must be null outside DELIVERED status", exception.getMessage());
    }

    @Test
    void restoreShouldRejectProcessingFieldsOutsideProcessingStatus() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.restore(
                        ReminderId.newId(),
                        TaskId.newId(),
                        createdAt.plusSeconds(300),
                        ReminderStatus.SCHEDULED,
                        createdAt,
                        createdAt,
                        createdAt.plusSeconds(300),
                        createdAt.plusSeconds(1),
                        "worker-a",
                        null,
                        0,
                        null
                )
        );

        assertEquals("processingStartedAt must be null outside PROCESSING status", exception.getMessage());
    }

    @Test
    void restoreShouldRejectScheduledReminderWithoutNextAttemptAt() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.restore(
                        ReminderId.newId(),
                        TaskId.newId(),
                        createdAt.plusSeconds(300),
                        ReminderStatus.SCHEDULED,
                        createdAt,
                        createdAt,
                        null,
                        null,
                        null,
                        null,
                        0,
                        null
                )
        );

        assertEquals("nextAttemptAt must not be null", exception.getMessage());
    }

    @Test
    void isDueAtShouldUseNextAttemptAtForScheduledReminder() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        assertFalse(reminder.isDueAt(now));
        assertTrue(reminder.isDueAt(now.plusSeconds(60)));
    }

    @Test
    void markProcessingShouldMoveReminderToProcessing() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        Instant claimedAt = now.plusSeconds(60);
        reminder.markProcessing("worker-a", claimedAt);

        assertEquals(ReminderStatus.PROCESSING, reminder.getStatus());
        assertEquals(claimedAt, reminder.getProcessingStartedAt());
        assertEquals("worker-a", reminder.getProcessingOwner());
        assertEquals(claimedAt, reminder.getUpdatedAt());
    }

    @Test
    void markProcessingShouldRejectBlankProcessorId() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> reminder.markProcessing(" ", now.plusSeconds(60))
        );

        assertEquals("processorId must not be blank", exception.getMessage());
    }

    @Test
    void markProcessingShouldRejectNullProcessorId() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> reminder.markProcessing(null, now.plusSeconds(60))
        );

        assertEquals("processorId must not be null", exception.getMessage());
    }

    @Test
    void markDeliveredShouldMoveReminderFromProcessingToDelivered() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        reminder.markProcessing("worker-a", now.plusSeconds(60));
        reminder.markDelivered(now.plusSeconds(61));

        assertEquals(ReminderStatus.DELIVERED, reminder.getStatus());
        assertEquals(now.plusSeconds(61), reminder.getDeliveredAt());
        assertEquals(1, reminder.getDeliveryAttempts());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void rescheduleShouldMoveReminderBackToScheduledAndTrackFailureReason() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        reminder.markProcessing("worker-a", now.plusSeconds(60));
        reminder.reschedule(now.plusSeconds(61), now.plusSeconds(120), "telegram timeout");

        assertEquals(ReminderStatus.SCHEDULED, reminder.getStatus());
        assertEquals(now.plusSeconds(120), reminder.getNextAttemptAt());
        assertEquals(1, reminder.getDeliveryAttempts());
        assertEquals("telegram timeout", reminder.getLastFailureReason());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
    }

    @Test
    void rescheduleShouldRejectNullNextAttemptAt() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        reminder.markProcessing("worker-a", now.plusSeconds(60));

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> reminder.reschedule(now.plusSeconds(61), null, "telegram timeout")
        );

        assertEquals("nextAttemptAt must not be null", exception.getMessage());
    }

    @Test
    void markFailedShouldMoveReminderToFailed() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        reminder.markProcessing("worker-a", now.plusSeconds(60));
        reminder.markFailed(now.plusSeconds(61), "recipient has no telegram chat");

        assertEquals(ReminderStatus.FAILED, reminder.getStatus());
        assertEquals(1, reminder.getDeliveryAttempts());
        assertEquals("recipient has no telegram chat", reminder.getLastFailureReason());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
    }

    @Test
    void markDeliveredShouldRejectIllegalTransitionFromScheduled() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(300), now);

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> reminder.markDelivered(now.plusSeconds(60))
        );

        assertEquals(
                "reminder cannot be marked as delivered from status: SCHEDULED",
                exception.getMessage()
        );
    }

    @Test
    void transitionShouldRejectTimestampsMovingBackwards() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(60), now);

        reminder.markProcessing("worker-a", now.plusSeconds(60));

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> reminder.markFailed(now.plusSeconds(59), "telegram timeout")
        );

        assertEquals("updatedAt must not move backwards", exception.getMessage());
    }
}
