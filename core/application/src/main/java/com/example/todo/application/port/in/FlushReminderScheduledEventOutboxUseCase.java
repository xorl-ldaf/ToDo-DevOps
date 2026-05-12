package com.example.todo.application.port.in;

import java.time.Instant;

public interface FlushReminderScheduledEventOutboxUseCase {
    ReminderScheduledEventOutboxReport flush(Instant now);
}
