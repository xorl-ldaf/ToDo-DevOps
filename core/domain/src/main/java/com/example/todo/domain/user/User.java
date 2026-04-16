package com.example.todo.domain.user;

import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.shared.exception.DomainValidationException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class User {
    private final UserId id;
    private final String username;
    private final String displayName;
    private TelegramChatId telegramChatId;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(
            UserId id,
            String username,
            String displayName,
            TelegramChatId telegramChatId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = requireNonNull(id, "id");
        this.username = requireText(username, "username");
        this.displayName = requireText(displayName, "displayName");
        this.telegramChatId = telegramChatId;
        this.createdAt = requireNonNull(createdAt, "createdAt");
        this.updatedAt = requireNonNull(updatedAt, "updatedAt");

        if (updatedAt.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt must not be before createdAt");
        }
    }

    public static User createNew(
            String username,
            String displayName,
            TelegramChatId telegramChatId,
            Instant now
    ) {
        Instant createdAt = requireNonNull(now, "now");
        return new User(
                UserId.newId(),
                username,
                displayName,
                telegramChatId,
                createdAt,
                createdAt
        );
    }

    public static User restore(
            UserId id,
            String username,
            String displayName,
            TelegramChatId telegramChatId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new User(
                id,
                username,
                displayName,
                telegramChatId,
                createdAt,
                updatedAt
        );
    }

    public void linkTelegramChat(TelegramChatId telegramChatId, Instant now) {
        this.telegramChatId = requireNonNull(telegramChatId, "telegramChatId");
        this.updatedAt = requireNonNull(now, "now");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
