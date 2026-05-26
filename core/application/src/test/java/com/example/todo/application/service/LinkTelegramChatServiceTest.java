package com.example.todo.application.service;

import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkTelegramChatServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-04-20T10:00:00Z");

    private final LinkTelegramChatService service = new LinkTelegramChatService();

    @Test
    void linkTelegramChatShouldReturnUserWithChatIdAndUpdatedTimestamp() {
        User user = user();
        Instant linkedAt = CREATED_AT.plusSeconds(300);
        TelegramChatId telegramChatId = new TelegramChatId(999999L);

        User linkedUser = service.linkTelegramChat(user, telegramChatId, linkedAt);

        assertEquals(user.getId(), linkedUser.getId());
        assertEquals(user.getUsername(), linkedUser.getUsername());
        assertEquals(user.getDisplayName(), linkedUser.getDisplayName());
        assertEquals(telegramChatId, linkedUser.getTelegramChatId());
        assertEquals(user.getCreatedAt(), linkedUser.getCreatedAt());
        assertEquals(linkedAt, linkedUser.getUpdatedAt());
        assertNull(user.getTelegramChatId());
        assertEquals(CREATED_AT, user.getUpdatedAt());
    }

    @Test
    void linkTelegramChatShouldRejectNullChatId() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.linkTelegramChat(user(), null, CREATED_AT.plusSeconds(60))
        );

        assertEquals("telegramChatId must not be null", exception.getMessage());
    }

    @Test
    void linkTelegramChatShouldRejectTimestampBeforeCreatedAt() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.linkTelegramChat(user(), new TelegramChatId(999999L), CREATED_AT.minusSeconds(1))
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
    }

    private User user() {
        return new User(
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                "alice",
                "Alice",
                null,
                CREATED_AT,
                CREATED_AT
        );
    }
}
