package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReminderFactoryTest {
    private static final Instant NOW = Instant.parse("2026-04-19T10:00:00Z");

    private final ReminderFactory factory = new ReminderFactory();

    @Test
    void createScheduledShouldInitializeReminderState() {
        TaskId taskId = TaskId.newId();
        Instant remindAt = NOW.plusSeconds(3600);

        Reminder reminder = factory.createScheduled(taskId, remindAt, NOW);

        assertNotNull(reminder.getId());
        assertEquals(taskId, reminder.getTaskId());
        assertEquals(remindAt, reminder.getRemindAt());
        assertEquals(ReminderStatus.SCHEDULED, reminder.getStatus());
        assertEquals(NOW, reminder.getCreatedAt());
        assertEquals(NOW, reminder.getUpdatedAt());
        assertEquals(remindAt, reminder.getNextAttemptAt());
        assertNull(reminder.getProcessingStartedAt());
        assertNull(reminder.getProcessingOwner());
        assertNull(reminder.getDeliveredAt());
        assertEquals(0, reminder.getDeliveryAttempts());
        assertNull(reminder.getLastFailureReason());
    }

    @Test
    void createScheduledShouldRejectReminderTimeInThePast() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> factory.createScheduled(TaskId.newId(), NOW.minusSeconds(1), NOW)
        );

        assertEquals("remindAt must not be in the past", exception.getMessage());
    }

    @Test
    void createScheduledShouldRejectMissingRequiredFields() {
        ApplicationValidationException taskIdException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.createScheduled(null, NOW.plusSeconds(60), NOW)
        );
        ApplicationValidationException remindAtException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.createScheduled(TaskId.newId(), null, NOW)
        );
        ApplicationValidationException nowException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.createScheduled(TaskId.newId(), NOW.plusSeconds(60), null)
        );

        assertEquals("taskId must not be null", taskIdException.getMessage());
        assertEquals("remindAt must not be null", remindAtException.getMessage());
        assertEquals("now must not be null", nowException.getMessage());
    }
}
