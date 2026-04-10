package com.example.todo.application.port.out;

import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;

import java.util.Optional;

public interface LoadTaskPort {
    Optional<Task> loadById(TaskId taskId);
}