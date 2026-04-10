package com.example.todo.application.port.in;

import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;

import java.util.Optional;

public interface GetTaskUseCase {
    Optional<Task> getTask(TaskId taskId);
}