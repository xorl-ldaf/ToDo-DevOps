package com.example.todo.application.port.out;

import java.util.Objects;

public record ReminderNotificationDeliveryResult(Status status, String reason) {
    public ReminderNotificationDeliveryResult {
        status = Objects.requireNonNull(status, "status must not be null");
        reason = status == Status.DELIVERED
                ? normalizeDeliveredReason(reason)
                : requireText(reason, "reason");
    }

    public static ReminderNotificationDeliveryResult delivered() {
        return new ReminderNotificationDeliveryResult(Status.DELIVERED, "delivered");
    }

    public static ReminderNotificationDeliveryResult skipped(String reason) {
        return new ReminderNotificationDeliveryResult(Status.SKIPPED, reason);
    }

    public static ReminderNotificationDeliveryResult retryableFailure(String reason) {
        return new ReminderNotificationDeliveryResult(Status.RETRYABLE_FAILURE, reason);
    }

    public static ReminderNotificationDeliveryResult permanentFailure(String reason) {
        return new ReminderNotificationDeliveryResult(Status.PERMANENT_FAILURE, reason);
    }

    public boolean deliveredSuccessfully() {
        return status == Status.DELIVERED;
    }

    public boolean retryableFailure() {
        return status == Status.RETRYABLE_FAILURE;
    }

    public boolean permanentFailure() {
        return status == Status.PERMANENT_FAILURE;
    }

    public boolean skipped() {
        return status == Status.SKIPPED;
    }

    public enum Status {
        DELIVERED,
        SKIPPED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
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
