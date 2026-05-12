package com.example.todo.application.outbox;

import com.example.todo.application.event.ReminderScheduledEventV1;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReminderScheduledEventOutboxMessage(
        UUID eventId,
        ReminderScheduledEventV1 event,
        int deliveryAttempts,
        Instant availableAt
) {
    public ReminderScheduledEventOutboxMessage {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        event = Objects.requireNonNull(event, "event must not be null");
        availableAt = Objects.requireNonNull(availableAt, "availableAt must not be null");
        if (deliveryAttempts < 0) {
            throw new IllegalArgumentException("deliveryAttempts must not be negative");
        }
    }
}
