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
    public User {
        id = requireNonNull(id, "id");
        username = requireText(username, "username");
        displayName = requireText(displayName, "displayName");
        createdAt = requireNonNull(createdAt, "createdAt");
        updatedAt = requireValidUpdatedAt(createdAt, updatedAt);
    }

    private static Instant requireValidUpdatedAt(Instant createdAt, Instant updatedAt) {
        Instant actualUpdatedAt = requireNonNull(updatedAt, "updatedAt");
        if (actualUpdatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        return actualUpdatedAt;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = requireNonNull(value, fieldName);
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

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
