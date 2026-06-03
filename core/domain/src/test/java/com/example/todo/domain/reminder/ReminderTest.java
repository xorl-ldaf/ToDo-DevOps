package com.example.todo.domain.reminder;

import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReminderTest {

    @Test
    void reminderShouldExposeStoredDataOnly() {
        ReminderId reminderId = new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        TaskId taskId = new TaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");
        Instant remindAt = Instant.parse("2026-04-19T11:00:00Z");

        Reminder reminder = new Reminder(
                reminderId,
                taskId,
                remindAt,
                ReminderStatus.SCHEDULED,
                createdAt,
                createdAt,
                remindAt,
                null,
                null,
                null,
                0,
                null
        );

        assertEquals(reminderId, reminder.getId());
        assertEquals(taskId, reminder.getTaskId());
        assertEquals(remindAt, reminder.getRemindAt());
        assertEquals(ReminderStatus.SCHEDULED, reminder.getStatus());
        assertEquals(createdAt, reminder.getCreatedAt());
        assertEquals(createdAt, reminder.getUpdatedAt());
        assertEquals(remindAt, reminder.getNextAttemptAt());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
        assertEquals(0, reminder.getDeliveryAttempts());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void markProcessingShouldReturnProcessingReminder() {
        Instant claimedAt = Instant.parse("2026-04-19T11:00:00Z");

        Reminder reminder = scheduledReminder().markProcessing("worker-a", claimedAt);

        assertEquals(ReminderStatus.PROCESSING, reminder.getStatus());
        assertEquals(claimedAt, reminder.getUpdatedAt());
        assertEquals(claimedAt, reminder.getProcessingStartedAt());
        assertEquals("worker-a", reminder.getProcessingOwner());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void markDeliveredShouldReturnDeliveredReminder() {
        Instant deliveredAt = Instant.parse("2026-04-19T11:30:00Z");

        Reminder reminder = processingReminder().markDelivered(deliveredAt);

        assertEquals(ReminderStatus.DELIVERED, reminder.getStatus());
        assertEquals(deliveredAt, reminder.getUpdatedAt());
        assertEquals(deliveredAt, reminder.getDeliveredAt());
        assertEquals(1, reminder.getDeliveryAttempts());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void rescheduleShouldReturnScheduledReminderWithNextAttempt() {
        Instant failedAt = Instant.parse("2026-04-19T11:30:00Z");
        Instant nextAttemptAt = Instant.parse("2026-04-19T12:00:00Z");

        Reminder reminder = processingReminder().reschedule(failedAt, nextAttemptAt, "temporary failure");

        assertEquals(ReminderStatus.SCHEDULED, reminder.getStatus());
        assertEquals(failedAt, reminder.getUpdatedAt());
        assertEquals(nextAttemptAt, reminder.getNextAttemptAt());
        assertEquals(1, reminder.getDeliveryAttempts());
        assertEquals("temporary failure", reminder.getLastFailureReason());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
    }

    @Test
    void markFailedShouldReturnFailedReminder() {
        Instant failedAt = Instant.parse("2026-04-19T11:30:00Z");

        Reminder reminder = processingReminder().markFailed(failedAt, "permanent failure");

        assertEquals(ReminderStatus.FAILED, reminder.getStatus());
        assertEquals(failedAt, reminder.getUpdatedAt());
        assertEquals(1, reminder.getDeliveryAttempts());
        assertEquals("permanent failure", reminder.getLastFailureReason());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
    }

    @Test
    void constructorShouldRejectInvalidRequiredState() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");

        IllegalArgumentException missingTaskException = assertThrows(
                IllegalArgumentException.class,
                () -> new Reminder(
                        new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                        null,
                        createdAt.plusSeconds(60),
                        ReminderStatus.SCHEDULED,
                        createdAt,
                        createdAt,
                        createdAt.plusSeconds(60),
                        null,
                        null,
                        null,
                        0,
                        null
                )
        );
        IllegalArgumentException attemptsException = assertThrows(
                IllegalArgumentException.class,
                () -> new Reminder(
                        new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                        new TaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                        createdAt.plusSeconds(60),
                        ReminderStatus.SCHEDULED,
                        createdAt,
                        createdAt,
                        createdAt.plusSeconds(60),
                        null,
                        null,
                        null,
                        -1,
                        null
                )
        );

        assertEquals("taskId must not be null", missingTaskException.getMessage());
        assertEquals("deliveryAttempts must not be negative", attemptsException.getMessage());
    }

    @Test
    void lifecycleMethodsShouldRejectInvalidTransitions() {
        Reminder scheduledReminder = scheduledReminder();

        IllegalStateException deliveredException = assertThrows(
                IllegalStateException.class,
                () -> scheduledReminder.markDelivered(Instant.parse("2026-04-19T11:30:00Z"))
        );
        IllegalStateException failedException = assertThrows(
                IllegalStateException.class,
                () -> scheduledReminder.markFailed(Instant.parse("2026-04-19T11:30:00Z"), "failure")
        );

        assertEquals("reminder cannot be marked as delivered from status: SCHEDULED", deliveredException.getMessage());
        assertEquals("reminder cannot be marked as failed from status: SCHEDULED", failedException.getMessage());
    }

    @Test
    void lifecycleMethodsShouldRejectInvalidInputs() {
        Reminder processingReminder = processingReminder();

        IllegalArgumentException processorException = assertThrows(
                IllegalArgumentException.class,
                () -> scheduledReminder().markProcessing(" ", Instant.parse("2026-04-19T11:30:00Z"))
        );
        IllegalArgumentException nextAttemptException = assertThrows(
                IllegalArgumentException.class,
                () -> processingReminder.reschedule(
                        Instant.parse("2026-04-19T11:30:00Z"),
                        Instant.parse("2026-04-19T11:29:59Z"),
                        "temporary failure"
                )
        );
        IllegalArgumentException failureReasonException = assertThrows(
                IllegalArgumentException.class,
                () -> processingReminder.markFailed(Instant.parse("2026-04-19T11:30:00Z"), " ")
        );

        assertEquals("processorId must not be blank", processorException.getMessage());
        assertEquals("nextAttemptAt must not be before the current processing time", nextAttemptException.getMessage());
        assertEquals("failureReason must not be blank", failureReasonException.getMessage());
    }

    private Reminder scheduledReminder() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");
        Instant remindAt = Instant.parse("2026-04-19T11:00:00Z");

        return new Reminder(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new TaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                remindAt,
                ReminderStatus.SCHEDULED,
                createdAt,
                createdAt,
                remindAt,
                null,
                null,
                null,
                0,
                null
        );
    }

    private Reminder processingReminder() {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");
        Instant processingStartedAt = Instant.parse("2026-04-19T11:00:00Z");

        return new Reminder(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new TaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                processingStartedAt,
                ReminderStatus.PROCESSING,
                createdAt,
                processingStartedAt,
                processingStartedAt,
                processingStartedAt,
                "worker-a",
                null,
                0,
                null
        );
    }
}
