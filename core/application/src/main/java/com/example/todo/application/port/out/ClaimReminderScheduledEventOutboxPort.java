package com.example.todo.application.port.out;

import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface ClaimReminderScheduledEventOutboxPort {
    List<ReminderScheduledEventOutboxMessage> claimPending(Instant now, String processorId, Duration processingTimeout, int limit);
}
