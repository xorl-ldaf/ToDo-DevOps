package com.example.todo.domain.user;

import com.example.todo.domain.shared.TelegramChatId;

import java.time.Instant;

public record User(
        UserId id,
        String username,
        String displayName,
        TelegramChatId telegramChatId,
        Instant createdAt,
        Instant updatedAt
) {
    public UserId getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TelegramChatId getTelegramChatId() {
        return telegramChatId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
