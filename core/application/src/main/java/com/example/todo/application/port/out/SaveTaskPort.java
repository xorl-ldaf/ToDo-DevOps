package com.example.todo.application.port.out;

import com.example.todo.domain.task.Task;

public interface SaveTaskPort {
    Task save(Task task);
}