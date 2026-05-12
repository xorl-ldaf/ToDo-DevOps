package com.example.todo.domain.shared;

public record TelegramChatId(Long value) {
    public TelegramChatId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("telegram chat id must be positive");
        }
    }
}