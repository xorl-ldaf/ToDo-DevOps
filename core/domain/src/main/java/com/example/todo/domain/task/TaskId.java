package com.example.todo.domain.task;

import java.util.UUID;

public record TaskId(UUID value) {
    public TaskId {
        if (value == null) {
            throw new IllegalArgumentException("task id must not be null");
        }
    }

    public static TaskId newId() {
        return new TaskId(UUID.randomUUID());
    }
}