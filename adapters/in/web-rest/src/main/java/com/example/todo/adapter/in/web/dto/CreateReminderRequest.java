package com.example.todo.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateReminderRequest(
        @NotNull Instant remindAt
) {
}