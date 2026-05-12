package com.example.todo.application.command;

import com.example.todo.domain.shared.TelegramChatId;

public record CreateUserCommand(
        String username,
        String displayName,
        TelegramChatId telegramChatId
) {
}