package com.example.todo.domain.task;

import com.example.todo.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

public record Task(
        TaskId id,
        UserId authorId,
        UserId assigneeId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static Task createNew(
            UserId authorId,
            UserId assigneeId,
            String title,
            String description,
            TaskPriority priority,
            Instant dueAt,
            Instant createdAt
    ) {
        UserId actualAuthorId = Objects.requireNonNull(authorId, "authorId must not be null");
        Instant actualCreatedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new Task(
                TaskId.newId(),
                actualAuthorId,
                assigneeId == null ? actualAuthorId : assigneeId,
                title,
                description == null ? "" : description,
                TaskStatus.OPEN,
                priority == null ? TaskPriority.MEDIUM : priority,
                dueAt,
                actualCreatedAt,
                actualCreatedAt
        );
    }

    public static Task restore(
            TaskId id,
            UserId authorId,
            UserId assigneeId,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            Instant dueAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Task(
                requireNonNull(id, "id"),
                requireNonNull(authorId, "authorId"),
                requireNonNull(assigneeId, "assigneeId"),
                title,
                description,
                requireNonNull(status, "status"),
                requireNonNull(priority, "priority"),
                dueAt,
                requireNonNull(createdAt, "createdAt"),
                requireNonNull(updatedAt, "updatedAt")
        );
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    public TaskId getId() {
        return id;
    }

    public UserId getAuthorId() {
        return authorId;
    }

    public UserId getAssigneeId() {
        return assigneeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
