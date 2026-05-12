package com.example.todo.application.service;

import com.example.todo.application.port.in.GetTaskUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;

import java.util.Objects;
import java.util.Optional;

public class GetTaskService implements GetTaskUseCase {
    private final LoadTaskPort loadTaskPort;

    public GetTaskService(LoadTaskPort loadTaskPort) {
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort);
    }

    @Override
    public Optional<Task> getTask(TaskId taskId) {
        return loadTaskPort.loadById(taskId);
    }
}