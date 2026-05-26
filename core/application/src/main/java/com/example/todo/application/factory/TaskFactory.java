package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.user.UserId;

import java.time.Instant;

public class TaskFactory {

    public Task create(
            UserId authorId,
            UserId assigneeId,
            String title,
            String description,
            TaskPriority priority,
            Instant dueAt,
            Instant now
    ) {
        Instant createdAt = requireNonNull(now, "now");
        UserId actualAuthorId = requireNonNull(authorId, "authorId");

        return Task.createNew(
                actualAuthorId,
                assigneeId == null ? actualAuthorId : assigneeId,
                requireText(title, "title"),
                description == null ? "" : description,
                priority == null ? TaskPriority.MEDIUM : priority,
                dueAt,
                createdAt
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApplicationValidationException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
