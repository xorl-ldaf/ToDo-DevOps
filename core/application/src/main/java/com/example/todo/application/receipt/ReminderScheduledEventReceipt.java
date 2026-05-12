package com.example.todo.application.receipt;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReminderScheduledEventReceipt(
        UUID eventId,
        UUID reminderId,
        UUID taskId,
        String topic,
        String eventVersion,
        Instant occurredAt,
        Instant consumedAt,
        int partition,
        long offset,
        String payload
) {
    public ReminderScheduledEventReceipt {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        reminderId = Objects.requireNonNull(reminderId, "reminderId must not be null");
        taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        topic = requireText(topic, "topic");
        eventVersion = requireText(eventVersion, "eventVersion");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        consumedAt = Objects.requireNonNull(consumedAt, "consumedAt must not be null");
        payload = requireText(payload, "payload");
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }
}
