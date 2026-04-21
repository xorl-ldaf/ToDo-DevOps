package com.example.todo.application.port.out;

import java.util.Objects;

public record ReminderNotificationDeliveryResult(Status status, String reason) {
    public ReminderNotificationDeliveryResult {
        status = Objects.requireNonNull(status, "status must not be null");
        reason = status == Status.SKIPPED
                ? requireText(reason, "reason")
                : normalizeDeliveredReason(reason);
    }

    public static ReminderNotificationDeliveryResult delivered() {
        return new ReminderNotificationDeliveryResult(Status.DELIVERED, "delivered");
    }

    public static ReminderNotificationDeliveryResult skipped(String reason) {
        return new ReminderNotificationDeliveryResult(Status.SKIPPED, reason);
    }

    public boolean deliveredSuccessfully() {
        return status == Status.DELIVERED;
    }

    public enum Status {
        DELIVERED,
        SKIPPED
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }

    private static String normalizeDeliveredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "delivered";
        }
        return reason;
    }
}
