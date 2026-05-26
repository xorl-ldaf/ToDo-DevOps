package com.example.todo.application.service;

import com.example.todo.application.port.in.ListTasksUseCase;
import com.example.todo.application.port.out.LoadAllTasksPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;

import java.util.Objects;

public class ListTasksService implements ListTasksUseCase {
    private final LoadAllTasksPort loadAllTasksPort;

    public ListTasksService(LoadAllTasksPort loadAllTasksPort) {
        this.loadAllTasksPort = Objects.requireNonNull(loadAllTasksPort);
    }

    @Override
    public PageResult<Task> listTasks(PageQuery pageQuery, TaskStatus status, TaskPriority priority) {
        return loadAllTasksPort.load(Objects.requireNonNull(pageQuery, "pageQuery must not be null"), status, priority);
    }
}
