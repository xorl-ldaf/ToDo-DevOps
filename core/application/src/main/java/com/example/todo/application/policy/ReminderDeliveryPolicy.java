package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.domain.reminder.Reminder;

public class ReminderDeliveryPolicy {

    public boolean shouldRetry(
            Reminder reminder,
            ReminderNotificationDeliveryResult deliveryResult,
            int maxDeliveryAttempts
    ) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        return shouldRetry(deliveryResult, actualReminder.getDeliveryAttempts(), maxDeliveryAttempts);
    }

    public boolean shouldRetry(
            ReminderNotificationDeliveryResult deliveryResult,
            int deliveryAttempts,
            int maxDeliveryAttempts
    ) {
        ReminderNotificationDeliveryResult actualDeliveryResult = requireNonNull(deliveryResult, "deliveryResult");
        if (deliveryAttempts < 0) {
            throw new ApplicationValidationException("deliveryAttempts must not be negative");
        }
        if (maxDeliveryAttempts < 1) {
            throw new ApplicationValidationException("maxDeliveryAttempts must be at least 1");
        }

        return actualDeliveryResult.retryableFailure()
                && deliveryAttempts + 1 < maxDeliveryAttempts;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
