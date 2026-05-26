package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;

import java.time.Instant;

public class ReminderLifecyclePolicy {

    public boolean isDueAt(Reminder reminder, Instant moment) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        Instant actualMoment = requireLifecycleValue(moment, "moment");
        return actualReminder.status() == ReminderStatus.SCHEDULED
                && !actualReminder.nextAttemptAt().isAfter(actualMoment);
    }

    public Reminder markProcessing(Reminder reminder, String processorId, Instant now) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        if (actualReminder.status() != ReminderStatus.SCHEDULED
                && actualReminder.status() != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be claimed for processing from status: " + actualReminder.status()
            );
        }

        Instant actualNow = requireValidUpdateTime(actualReminder, now);
        return new Reminder(
                actualReminder.id(),
                actualReminder.taskId(),
                actualReminder.remindAt(),
                ReminderStatus.PROCESSING,
                actualReminder.createdAt(),
                actualNow,
                actualReminder.nextAttemptAt(),
                actualNow,
                requireText(processorId, "processorId"),
                actualReminder.deliveredAt(),
                actualReminder.deliveryAttempts(),
                null
        );
    }

    public Reminder markDelivered(Reminder reminder, Instant now) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        if (actualReminder.status() != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be marked as delivered from status: " + actualReminder.status()
            );
        }

        Instant actualNow = requireValidDeliveredTime(actualReminder, now);
        return new Reminder(
                actualReminder.id(),
                actualReminder.taskId(),
                actualReminder.remindAt(),
                ReminderStatus.DELIVERED,
                actualReminder.createdAt(),
                actualNow,
                actualReminder.nextAttemptAt(),
                null,
                null,
                actualNow,
                actualReminder.deliveryAttempts() + 1,
                null
        );
    }

    public Reminder reschedule(Reminder reminder, Instant now, Instant nextAttemptAt, String failureReason) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        if (actualReminder.status() != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be rescheduled from status: " + actualReminder.status()
            );
        }

        Instant actualNow = requireValidUpdateTime(actualReminder, now);
        Instant actualNextAttemptAt = requireLifecycleValue(nextAttemptAt, "nextAttemptAt");
        if (actualNextAttemptAt.isBefore(actualNow)) {
            throw new ApplicationValidationException("nextAttemptAt must not be before the current processing time");
        }

        return new Reminder(
                actualReminder.id(),
                actualReminder.taskId(),
                actualReminder.remindAt(),
                ReminderStatus.SCHEDULED,
                actualReminder.createdAt(),
                actualNow,
                actualNextAttemptAt,
                null,
                null,
                null,
                actualReminder.deliveryAttempts() + 1,
                requireText(failureReason, "failureReason")
        );
    }

    public Reminder markFailed(Reminder reminder, Instant now, String failureReason) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        if (actualReminder.status() != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be marked as failed from status: " + actualReminder.status()
            );
        }

        Instant actualNow = requireValidUpdateTime(actualReminder, now);
        return new Reminder(
                actualReminder.id(),
                actualReminder.taskId(),
                actualReminder.remindAt(),
                ReminderStatus.FAILED,
                actualReminder.createdAt(),
                actualNow,
                actualReminder.nextAttemptAt(),
                null,
                null,
                null,
                actualReminder.deliveryAttempts() + 1,
                requireText(failureReason, "failureReason")
        );
    }

    private static Instant requireValidUpdateTime(Reminder reminder, Instant now) {
        Instant actualNow = requireLifecycleValue(now, "now");
        if (actualNow.isBefore(reminder.createdAt())) {
            throw new ApplicationValidationException("updatedAt must not be before createdAt");
        }
        if (actualNow.isBefore(reminder.updatedAt())) {
            throw new ApplicationValidationException("updatedAt must not move backwards");
        }
        return actualNow;
    }

    private static Instant requireValidDeliveredTime(Reminder reminder, Instant now) {
        Instant actualNow = requireLifecycleValue(now, "now");
        if (actualNow.isBefore(reminder.createdAt())) {
            throw new ApplicationValidationException("deliveredAt must not be before createdAt");
        }
        if (actualNow.isBefore(reminder.updatedAt())) {
            throw new ApplicationValidationException("updatedAt must not move backwards");
        }
        return actualNow;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = requireLifecycleValue(value, fieldName);
        if (actualValue.isBlank()) {
            throw new ApplicationValidationException(fieldName + " must not be blank");
        }
        return actualValue;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }

    private static <T> T requireLifecycleValue(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
