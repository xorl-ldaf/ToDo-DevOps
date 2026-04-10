package com.example.todo.domain.user;

import com.example.todo.domain.shared.TelegramChatId;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class User {
    private final UserId id;
    private final String username;
    private final String displayName;
    private TelegramChatId telegramChatId;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(
            UserId id,
            String username,
            String displayName,
            TelegramChatId telegramChatId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = requireText(username, "username");
        this.displayName = requireText(displayName, "displayName");
        this.telegramChatId = telegramChatId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public void linkTelegramChat(TelegramChatId telegramChatId, Instant now) {
        this.telegramChatId = Objects.requireNonNull(telegramChatId, "telegramChatId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}