package com.example.todo.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String displayName,
        Long telegramChatId
) {
}