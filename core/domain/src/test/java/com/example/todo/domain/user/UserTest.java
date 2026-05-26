package com.example.todo.domain.user;

import com.example.todo.domain.shared.TelegramChatId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserTest {

    @Test
    void userShouldExposeStoredDataOnly() {
        UserId userId = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        TelegramChatId telegramChatId = new TelegramChatId(123456789L);
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-20T10:05:00Z");

        User user = new User(
                userId,
                "alice",
                "Alice",
                telegramChatId,
                createdAt,
                updatedAt
        );

        assertEquals(userId, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getDisplayName());
        assertEquals(telegramChatId, user.getTelegramChatId());
        assertEquals(createdAt, user.getCreatedAt());
        assertEquals(updatedAt, user.getUpdatedAt());
    }

    @Test
    void userShouldNotExposeBusinessMethods() {
        Set<String> methods = Arrays.stream(User.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertFalse(methods.contains("createNew"));
        assertFalse(methods.contains("restore"));
        assertFalse(methods.contains("linkTelegramChat"));
    }
}
