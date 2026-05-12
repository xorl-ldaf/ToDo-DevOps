package com.example.todo.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface FinalizeReminderScheduledEventOutboxPort {
    boolean markPublished(UUID eventId, String processorId, Instant publishedAt);

    boolean reschedule(UUID eventId, String processorId, Instant processedAt, Instant nextAttemptAt, String failureReason);

    boolean markFailed(UUID eventId, String processorId, Instant processedAt, String failureReason);
}
