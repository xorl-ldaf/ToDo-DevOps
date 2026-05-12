package com.example.todo.config;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;

public final class NoOpReminderScheduledEventPublisher implements PublishReminderScheduledEventPort {

    @Override
    public void publish(ReminderScheduledEventV1 event) {
        // Kafka stays opt-in for tests and non-integration runtimes.
        // The application service still depends on the explicit outbound port.
    }
}
