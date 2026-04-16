package com.example.todo.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID authorId,
        UUID assigneeId,
        String title,
        String description,
        TaskStatusDto status,
        TaskPriorityDto priority,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt
) {
}