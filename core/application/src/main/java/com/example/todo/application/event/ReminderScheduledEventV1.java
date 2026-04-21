package com.example.todo.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReminderScheduledEventV1(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID reminderId,
        UUID taskId,
        Instant remindAt,
        String status
) {
    public static final String EVENT_TYPE = "reminder.scheduled";
    public static final String EVENT_VERSION = "v1";

    public ReminderScheduledEventV1 {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = requireEquals(EVENT_TYPE, eventType, "eventType");
        eventVersion = requireEquals(EVENT_VERSION, eventVersion, "eventVersion");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        reminderId = Objects.requireNonNull(reminderId, "reminderId must not be null");
        taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        remindAt = Objects.requireNonNull(remindAt, "remindAt must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
    }

    private static String requireEquals(String expected, String actual, String fieldName) {
        String value = Objects.requireNonNull(actual, fieldName + " must not be null");
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(fieldName + " must be " + expected);
        }
        return value;
    }
}
