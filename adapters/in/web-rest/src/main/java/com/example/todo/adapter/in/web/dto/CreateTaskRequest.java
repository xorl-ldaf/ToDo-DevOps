package com.example.todo.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000)
        String description,
        @NotNull UUID authorId,
        UUID assigneeId,
        TaskPriorityDto priority,
        Instant dueAt
) {
}
