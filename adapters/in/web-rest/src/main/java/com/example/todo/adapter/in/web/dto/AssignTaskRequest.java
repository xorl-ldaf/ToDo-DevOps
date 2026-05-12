package com.example.todo.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignTaskRequest(
        @NotNull UUID assigneeId
) {
}