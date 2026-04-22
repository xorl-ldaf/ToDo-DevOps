package com.example.todo.config;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.out.StoreReminderScheduledEventPort;

public final class NoOpStoreReminderScheduledEventPort implements StoreReminderScheduledEventPort {

    @Override
    public void store(ReminderScheduledEventV1 event) {
        // Kafka integration is explicitly opt-in. When disabled, reminder creation
        // completes without creating an integration event contract.
    }
}
