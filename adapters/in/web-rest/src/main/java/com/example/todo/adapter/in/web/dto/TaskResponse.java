package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID authorId,
        UUID assigneeId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt
) {
}