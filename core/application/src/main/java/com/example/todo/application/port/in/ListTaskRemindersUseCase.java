package com.example.todo.application.port.in;

import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.util.List;

public interface ListTaskRemindersUseCase {
    List<Reminder> listTaskReminders(TaskId taskId);
}