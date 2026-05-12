package com.example.todo.domain.user;

import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("user id must not be null");
        }
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}