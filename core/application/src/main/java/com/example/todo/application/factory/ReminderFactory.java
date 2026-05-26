package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;

import java.time.Instant;

public class ReminderFactory {

    public Reminder createScheduled(TaskId taskId, Instant remindAt, Instant now) {
        Instant createdAt = requireNonNull(now, "now");
        Instant actualRemindAt = requireNonNull(remindAt, "remindAt");

        if (actualRemindAt.isBefore(createdAt)) {
            throw new ApplicationValidationException("remindAt must not be in the past");
        }

        return new Reminder(
                ReminderId.newId(),
                requireNonNull(taskId, "taskId"),
                actualRemindAt,
                ReminderStatus.SCHEDULED,
                createdAt,
                createdAt,
                actualRemindAt,
                null,
                null,
                null,
                0,
                null
        );
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
