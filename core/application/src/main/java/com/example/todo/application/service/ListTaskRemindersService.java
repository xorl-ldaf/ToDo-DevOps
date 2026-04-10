package com.example.todo.application.service;

import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.util.List;
import java.util.Objects;

public class ListTaskRemindersService implements ListTaskRemindersUseCase {
    private final LoadTaskRemindersPort loadTaskRemindersPort;

    public ListTaskRemindersService(LoadTaskRemindersPort loadTaskRemindersPort) {
        this.loadTaskRemindersPort = Objects.requireNonNull(loadTaskRemindersPort);
    }

    @Override
    public List<Reminder> listTaskReminders(TaskId taskId) {
        return loadTaskRemindersPort.loadByTaskId(taskId);
    }
}