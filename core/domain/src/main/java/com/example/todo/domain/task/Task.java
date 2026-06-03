package com.example.todo.domain.task;

import com.example.todo.domain.user.UserId;

import java.time.Instant;

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
    public Task {
        id = requireNonNull(id, "id");
        authorId = requireNonNull(authorId, "authorId");
        assigneeId = requireNonNull(assigneeId, "assigneeId");
        title = requireText(title, "title");
        description = description == null ? "" : description;
        status = requireNonNull(status, "status");
        priority = requireNonNull(priority, "priority");
        createdAt = requireNonNull(createdAt, "createdAt");
        updatedAt = requireValidUpdatedAt(createdAt, updatedAt);
    }

    public static Task createNew(
            UserId authorId,
            UserId assigneeId,
            String title,
            String description,
            TaskPriority priority,
            Instant dueAt,
            Instant createdAt
    ) {
        UserId actualAuthorId = requireNonNull(authorId, "authorId");
        Instant actualCreatedAt = requireNonNull(createdAt, "createdAt");
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

    public Task assignTo(UserId newAssigneeId, Instant updatedAt) {
        if (status == TaskStatus.DONE || status == TaskStatus.CANCELLED) {
            throw new IllegalStateException("task cannot be reassigned from status: " + status);
        }

        Instant actualUpdatedAt = requireNonNull(updatedAt, "updatedAt");
        if (actualUpdatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (actualUpdatedAt.isBefore(this.updatedAt)) {
            throw new IllegalArgumentException("updatedAt must not move backwards");
        }

        TaskStatus nextStatus = status == TaskStatus.OPEN
                ? TaskStatus.IN_PROGRESS
                : status;

        return Task.restore(
                id,
                authorId,
                requireNonNull(newAssigneeId, "newAssigneeId"),
                title,
                description,
                nextStatus,
                priority,
                dueAt,
                createdAt,
                actualUpdatedAt
        );
    }

    private static Instant requireValidUpdatedAt(Instant createdAt, Instant updatedAt) {
        Instant actualUpdatedAt = requireNonNull(updatedAt, "updatedAt");
        if (actualUpdatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        return actualUpdatedAt;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = requireNonNull(value, fieldName);
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
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
