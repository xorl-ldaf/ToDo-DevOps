package com.example.todo.application.port.in;

import com.example.todo.domain.task.Task;

import java.util.List;

public interface ListTasksUseCase {
    List<Task> listTasks();
}