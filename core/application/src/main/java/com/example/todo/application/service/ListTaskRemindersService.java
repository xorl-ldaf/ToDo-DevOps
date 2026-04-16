package com.example.todo.application.service;

import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.util.List;
import java.util.Objects;

public class ListTaskRemindersService implements ListTaskRemindersUseCase {
    private final LoadTaskPort loadTaskPort;
    private final LoadTaskRemindersPort loadTaskRemindersPort;

    public ListTaskRemindersService(
            LoadTaskPort loadTaskPort,
            LoadTaskRemindersPort loadTaskRemindersPort
    ) {
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
        this.loadTaskRemindersPort = Objects.requireNonNull(loadTaskRemindersPort, "loadTaskRemindersPort must not be null");
    }

    @Override
    public List<Reminder> listTaskReminders(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId must not be null");

        if (loadTaskPort.loadById(taskId).isEmpty()) {
            throw new ResourceNotFoundException("task not found: " + taskId);
        }

        return loadTaskRemindersPort.loadByTaskId(taskId);
    }
}