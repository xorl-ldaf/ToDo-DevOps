package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;

public interface SaveReminderPort {
    Reminder save(Reminder reminder);
}