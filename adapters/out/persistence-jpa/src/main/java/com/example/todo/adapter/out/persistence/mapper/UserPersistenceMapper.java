package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.UserJpaEntity;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

public final class UserPersistenceMapper {

    private UserPersistenceMapper() {
    }

    public static UserJpaEntity toJpa(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.getId().value());
        entity.setUsername(user.getUsername());
        entity.setDisplayName(user.getDisplayName());
        entity.setTelegramChatId(
                user.getTelegramChatId() == null ? null : user.getTelegramChatId().value()
        );
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    public static User toDomain(UserJpaEntity entity) {
        return User.restore(
                new UserId(entity.getId()),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getTelegramChatId() == null ? null : new TelegramChatId(entity.getTelegramChatId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
