package com.example.todo.domain.reminder;

import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void reminderShouldNotExposeLifecycleMethods() {
        Set<String> methods = Arrays.stream(Reminder.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertFalse(methods.contains("schedule"));
        assertFalse(methods.contains("isDueAt"));
        assertFalse(methods.contains("markProcessing"));
        assertFalse(methods.contains("markDelivered"));
        assertFalse(methods.contains("reschedule"));
        assertFalse(methods.contains("markFailed"));
    }
}
