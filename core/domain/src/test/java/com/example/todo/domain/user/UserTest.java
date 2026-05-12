package com.example.todo.domain.user;

import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void createNewShouldSetFieldsAndConsistentTimestamps() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        TelegramChatId telegramChatId = new TelegramChatId(123456789L);

        User user = User.createNew("alice", "Alice", telegramChatId, now);

        assertNotNull(user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getDisplayName());
        assertEquals(telegramChatId, user.getTelegramChatId());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    void createNewShouldRejectBlankUsername() {
        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> User.createNew("   ", "Alice", null, Instant.parse("2026-04-20T10:00:00Z"))
        );

        assertEquals("username must not be blank", exception.getMessage());
    }

    @Test
    void createNewShouldRejectBlankDisplayName() {
        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> User.createNew("alice", "", null, Instant.parse("2026-04-20T10:00:00Z"))
        );

        assertEquals("displayName must not be blank", exception.getMessage());
    }

    @Test
    void restoreShouldRejectUpdatedAtBeforeCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> User.restore(
                        userId("11111111-1111-1111-1111-111111111111"),
                        "alice",
                        "Alice",
                        null,
                        createdAt,
                        createdAt.minusSeconds(1)
                )
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
    }

    @Test
    void linkTelegramChatShouldUpdateChatIdAndTimestamp() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant linkedAt = createdAt.plusSeconds(300);
        User user = User.restore(
                userId("11111111-1111-1111-1111-111111111111"),
                "alice",
                "Alice",
                null,
                createdAt,
                createdAt
        );
        TelegramChatId telegramChatId = new TelegramChatId(999999L);

        user.linkTelegramChat(telegramChatId, linkedAt);

        assertEquals(telegramChatId, user.getTelegramChatId());
        assertEquals(linkedAt, user.getUpdatedAt());
    }

    @Test
    void linkTelegramChatShouldRejectNullChatId() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        User user = User.restore(
                userId("11111111-1111-1111-1111-111111111111"),
                "alice",
                "Alice",
                null,
                createdAt,
                createdAt
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> user.linkTelegramChat(null, createdAt.plusSeconds(60))
        );

        assertEquals("telegramChatId must not be null", exception.getMessage());
        assertNull(user.getTelegramChatId());
        assertEquals(createdAt, user.getUpdatedAt());
    }

    @Test
    void linkTelegramChatShouldRejectTimestampBeforeCreatedAtWithoutMutatingState() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        User user = User.restore(
                userId("11111111-1111-1111-1111-111111111111"),
                "alice",
                "Alice",
                null,
                createdAt,
                createdAt
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> user.linkTelegramChat(new TelegramChatId(999999L), createdAt.minusSeconds(1))
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
        assertNull(user.getTelegramChatId());
        assertEquals(createdAt, user.getUpdatedAt());
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
