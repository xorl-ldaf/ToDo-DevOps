package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        @NotNull UUID authorId,
        UUID assigneeId,
        TaskPriority priority,
        Instant dueAt
) {
}