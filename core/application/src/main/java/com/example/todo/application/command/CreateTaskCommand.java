package com.example.todo.application.command;

import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.user.UserId;

import java.time.Instant;

public record CreateTaskCommand(
        String title,
        String description,
        UserId authorId,
        UserId assigneeId,
        TaskPriority priority,
        Instant dueAt
) {
}