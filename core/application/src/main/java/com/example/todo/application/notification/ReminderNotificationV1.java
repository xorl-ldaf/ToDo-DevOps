package com.example.todo.application.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReminderNotificationV1(
        UUID notificationId,
        String notificationType,
        String notificationVersion,
        Instant occurredAt,
        UUID reminderId,
        UUID taskId,
        String taskTitle,
        String taskDescription,
        Instant remindAt,
        UUID recipientUserId,
        String recipientDisplayName,
        Long recipientTelegramChatId
) {
    public static final String NOTIFICATION_TYPE = "reminder.notification";
    public static final String NOTIFICATION_VERSION = "v1";

    public ReminderNotificationV1 {
        notificationId = Objects.requireNonNull(notificationId, "notificationId must not be null");
        notificationType = requireEquals(NOTIFICATION_TYPE, notificationType, "notificationType");
        notificationVersion = requireEquals(NOTIFICATION_VERSION, notificationVersion, "notificationVersion");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        reminderId = Objects.requireNonNull(reminderId, "reminderId must not be null");
        taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        taskTitle = requireText(taskTitle, "taskTitle");
        taskDescription = Objects.requireNonNull(taskDescription, "taskDescription must not be null");
        remindAt = Objects.requireNonNull(remindAt, "remindAt must not be null");
        recipientUserId = Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        recipientDisplayName = requireText(recipientDisplayName, "recipientDisplayName");
        if (recipientTelegramChatId == null || recipientTelegramChatId <= 0) {
            throw new IllegalArgumentException("recipientTelegramChatId must be positive");
        }
    }

    private static String requireEquals(String expected, String actual, String fieldName) {
        String value = Objects.requireNonNull(actual, fieldName + " must not be null");
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(fieldName + " must be " + expected);
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }
}
