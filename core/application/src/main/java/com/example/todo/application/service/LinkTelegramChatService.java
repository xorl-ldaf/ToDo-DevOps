package com.example.todo.application.service;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.user.User;

import java.time.Instant;

public class LinkTelegramChatService {

    public User linkTelegramChat(User user, TelegramChatId telegramChatId, Instant now) {
        User actualUser = requireNonNull(user, "user");
        TelegramChatId actualTelegramChatId = requireLifecycleValue(telegramChatId, "telegramChatId");
        Instant actualNow = requireLifecycleValue(now, "now");

        if (actualNow.isBefore(actualUser.createdAt())) {
            throw new ApplicationValidationException("updatedAt must not be before createdAt");
        }

        return new User(
                actualUser.id(),
                actualUser.username(),
                actualUser.displayName(),
                actualTelegramChatId,
                actualUser.createdAt(),
                actualNow
        );
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }

    private static <T> T requireLifecycleValue(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
