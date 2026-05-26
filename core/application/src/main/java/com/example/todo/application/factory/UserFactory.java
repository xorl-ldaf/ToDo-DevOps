package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

import java.time.Instant;

public class UserFactory {

    public User create(
            String username,
            String displayName,
            TelegramChatId telegramChatId,
            Instant now
    ) {
        Instant createdAt = requireNonNull(now, "now");
        return new User(
                UserId.newId(),
                requireText(username, "username"),
                requireText(displayName, "displayName"),
                telegramChatId,
                createdAt,
                createdAt
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApplicationValidationException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
