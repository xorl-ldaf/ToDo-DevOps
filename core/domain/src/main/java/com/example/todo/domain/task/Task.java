package com.example.todo.domain.task;

import com.example.todo.domain.user.UserId;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class Task {
    private final TaskId id;
    private final UserId authorId;
    private UserId assigneeId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private Instant dueAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public Task(
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
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.authorId = Objects.requireNonNull(authorId, "authorId must not be null");
        this.assigneeId = Objects.requireNonNull(assigneeId, "assigneeId must not be null");
        this.title = requireText(title, "title");
        this.description = description == null ? "" : description;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        this.dueAt = dueAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public void assignTo(UserId newAssigneeId, Instant now) {
        this.assigneeId = Objects.requireNonNull(newAssigneeId, "newAssigneeId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        if (this.status == TaskStatus.OPEN) {
            this.status = TaskStatus.IN_PROGRESS;
        }
    }

    public void markCompleted(Instant now) {
        this.status = TaskStatus.DONE;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}