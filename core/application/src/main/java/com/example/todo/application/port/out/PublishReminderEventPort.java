package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;

public interface PublishReminderEventPort {
    void publish(Reminder reminder);
}