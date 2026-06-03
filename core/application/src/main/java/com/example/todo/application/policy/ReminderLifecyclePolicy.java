package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;

import java.time.Instant;
import java.util.function.Supplier;

public class ReminderLifecyclePolicy {

    public boolean isDueAt(Reminder reminder, Instant moment) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        Instant actualMoment = requireLifecycleValue(moment, "moment");
        return actualReminder.status() == ReminderStatus.SCHEDULED
                && !actualReminder.nextAttemptAt().isAfter(actualMoment);
    }

    public Reminder markProcessing(Reminder reminder, String processorId, Instant now) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        return transition(() -> actualReminder.markProcessing(processorId, requireLifecycleValue(now, "now")));
    }

    public Reminder markDelivered(Reminder reminder, Instant now) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        return transition(() -> actualReminder.markDelivered(requireLifecycleValue(now, "now")));
    }

    public Reminder reschedule(Reminder reminder, Instant now, Instant nextAttemptAt, String failureReason) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        return transition(() -> actualReminder.reschedule(
                requireLifecycleValue(now, "now"),
                nextAttemptAt,
                failureReason
        ));
    }

    public Reminder markFailed(Reminder reminder, Instant now, String failureReason) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        return transition(() -> actualReminder.markFailed(requireLifecycleValue(now, "now"), failureReason));
    }

    private static Reminder transition(Supplier<Reminder> transition) {
        try {
            return transition.get();
        } catch (IllegalStateException ex) {
            throw new InvalidStateTransitionException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ApplicationValidationException(ex.getMessage());
        }
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
