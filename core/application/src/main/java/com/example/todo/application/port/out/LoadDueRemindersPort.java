package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;

import java.time.Instant;
import java.util.List;

public interface LoadDueRemindersPort {
    List<Reminder> loadDueReminders(Instant now);
}