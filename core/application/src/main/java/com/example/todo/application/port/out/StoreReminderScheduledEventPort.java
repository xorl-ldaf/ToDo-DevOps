package com.example.todo.application.port.out;

import com.example.todo.application.event.ReminderScheduledEventV1;

public interface StoreReminderScheduledEventPort {
    void store(ReminderScheduledEventV1 event);
}
