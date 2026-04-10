package com.example.todo.application.port.in;

import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.domain.reminder.Reminder;

public interface CreateReminderUseCase {
    Reminder createReminder(CreateReminderCommand command);
}