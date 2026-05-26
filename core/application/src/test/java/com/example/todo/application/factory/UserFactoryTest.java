package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserFactoryTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    private final UserFactory factory = new UserFactory();

    @Test
    void createShouldGenerateIdAndSetConsistentTimestamps() {
        TelegramChatId telegramChatId = new TelegramChatId(123456789L);

        User user = factory.create("alice", "Alice", telegramChatId, NOW);

        assertNotNull(user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getDisplayName());
        assertEquals(telegramChatId, user.getTelegramChatId());
        assertEquals(NOW, user.getCreatedAt());
        assertEquals(NOW, user.getUpdatedAt());
    }

    @Test
    void createShouldRejectBlankUsername() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create("   ", "Alice", null, NOW)
        );

        assertEquals("username must not be blank", exception.getMessage());
    }

    @Test
    void createShouldRejectBlankDisplayName() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create("alice", "", null, NOW)
        );

        assertEquals("displayName must not be blank", exception.getMessage());
    }

    @Test
    void createShouldRejectNullCurrentTime() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create("alice", "Alice", null, null)
        );

        assertEquals("now must not be null", exception.getMessage());
    }
}
