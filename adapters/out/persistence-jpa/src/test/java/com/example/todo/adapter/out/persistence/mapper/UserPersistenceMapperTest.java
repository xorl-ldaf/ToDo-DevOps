package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.UserJpaEntity;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPersistenceMapperTest {

    @Test
    void toJpaShouldMapEveryField() {
        User user = user();

        UserJpaEntity entity = UserPersistenceMapper.toJpa(user);

        assertThat(entity.getId()).isEqualTo(user.getId().value());
        assertThat(entity.getUsername()).isEqualTo(user.getUsername());
        assertThat(entity.getDisplayName()).isEqualTo(user.getDisplayName());
        assertThat(entity.getTelegramChatId()).isEqualTo(user.getTelegramChatId().value());
        assertThat(entity.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    void toJpaShouldPreserveMissingTelegramChatId() {
        User user = user(null);

        UserJpaEntity entity = UserPersistenceMapper.toJpa(user);

        assertThat(entity.getTelegramChatId()).isNull();
    }

    @Test
    void toDomainShouldMapEveryField() {
        UserJpaEntity entity = userEntity(123456789L);

        User user = UserPersistenceMapper.toDomain(entity);

        assertThat(user.getId().value()).isEqualTo(entity.getId());
        assertThat(user.getUsername()).isEqualTo(entity.getUsername());
        assertThat(user.getDisplayName()).isEqualTo(entity.getDisplayName());
        assertThat(user.getTelegramChatId().value()).isEqualTo(entity.getTelegramChatId());
        assertThat(user.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(user.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    void toDomainShouldPreserveMissingTelegramChatId() {
        UserJpaEntity entity = userEntity(null);

        User user = UserPersistenceMapper.toDomain(entity);

        assertThat(user.getTelegramChatId()).isNull();
    }

    @Test
    void shouldRoundTripUserWithoutLosingFields() {
        User user = user();

        User roundTrip = UserPersistenceMapper.toDomain(UserPersistenceMapper.toJpa(user));

        assertThat(roundTrip).isEqualTo(user);
    }

    private User user() {
        return user(new TelegramChatId(123456789L));
    }

    private User user(TelegramChatId telegramChatId) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        return new User(
                new UserId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                "alice",
                "Alice",
                telegramChatId,
                createdAt,
                createdAt.plusSeconds(300)
        );
    }

    private UserJpaEntity userEntity(Long telegramChatId) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-12T09:05:00Z");
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        entity.setUsername("alice");
        entity.setDisplayName("Alice");
        entity.setTelegramChatId(telegramChatId);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
