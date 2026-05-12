package com.example.todo.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        Long telegramChatId,
        Instant createdAt,
        Instant updatedAt
) {
}