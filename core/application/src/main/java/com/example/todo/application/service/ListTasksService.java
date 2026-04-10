package com.example.todo.application.service;

import com.example.todo.application.port.in.ListTasksUseCase;
import com.example.todo.application.port.out.LoadAllTasksPort;
import com.example.todo.domain.task.Task;

import java.util.List;
import java.util.Objects;

public class ListTasksService implements ListTasksUseCase {
    private final LoadAllTasksPort loadAllTasksPort;

    public ListTasksService(LoadAllTasksPort loadAllTasksPort) {
        this.loadAllTasksPort = Objects.requireNonNull(loadAllTasksPort);
    }

    @Override
    public List<Task> listTasks() {
        return loadAllTasksPort.loadAll();
    }
}