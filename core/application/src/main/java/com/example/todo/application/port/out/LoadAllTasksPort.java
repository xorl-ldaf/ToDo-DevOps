package com.example.todo.application.port.out;

import com.example.todo.domain.task.Task;

import java.util.List;

public interface LoadAllTasksPort {
    List<Task> loadAll();
}