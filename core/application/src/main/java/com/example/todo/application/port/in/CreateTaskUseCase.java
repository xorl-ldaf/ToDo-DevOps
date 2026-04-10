package com.example.todo.application.port.in;

import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.domain.task.Task;

public interface CreateTaskUseCase {
    Task createTask(CreateTaskCommand command);
}