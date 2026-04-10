package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.util.List;

public interface LoadTaskRemindersPort {
    List<Reminder> loadByTaskId(TaskId taskId);
}