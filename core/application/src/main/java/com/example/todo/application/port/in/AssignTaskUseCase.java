package com.example.todo.application.port.in;

import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.domain.task.Task;

public interface AssignTaskUseCase {
    Task assignTask(AssignTaskCommand command);
}