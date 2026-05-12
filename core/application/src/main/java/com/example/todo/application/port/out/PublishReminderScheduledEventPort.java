package com.example.todo.application.port.out;

import com.example.todo.application.event.ReminderScheduledEventV1;

public interface PublishReminderScheduledEventPort {
    void publish(ReminderScheduledEventV1 event);
}
