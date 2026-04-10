package com.example.todo.domain.reminder;

import java.util.UUID;

public record ReminderId(UUID value) {
    public ReminderId {
        if (value == null) {
            throw new IllegalArgumentException("reminder id must not be null");
        }
    }

    public static ReminderId newId() {
        return new ReminderId(UUID.randomUUID());
    }
}