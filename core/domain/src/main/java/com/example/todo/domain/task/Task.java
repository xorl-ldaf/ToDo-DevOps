package com.example.todo.domain.task;

import com.example.todo.domain.shared.exception.DomainValidationException;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
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

    private Task(
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
        this.id = requireNonNull(id, "id");
        this.authorId = requireNonNull(authorId, "authorId");
        this.assigneeId = requireNonNull(assigneeId, "assigneeId");
        this.title = requireText(title, "title");
        this.description = description == null ? "" : description;
        this.status = requireNonNull(status, "status");
        this.priority = requireNonNull(priority, "priority");
        this.dueAt = dueAt;
        this.createdAt = requireNonNull(createdAt, "createdAt");
        this.updatedAt = requireNonNull(updatedAt, "updatedAt");

        if (updatedAt.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt must not be before createdAt");
        }
    }

    public static Task createNew(
            UserId authorId,
            UserId assigneeId,
            String title,
            String description,
            TaskPriority priority,
            Instant dueAt,
            Instant now
    ) {
        Instant createdAt = requireNonNull(now, "now");
        UserId actualAssigneeId = assigneeId == null ? authorId : assigneeId;

        return new Task(
                TaskId.newId(),
                authorId,
                actualAssigneeId,
                title,
                description,
                TaskStatus.OPEN,
                priority == null ? TaskPriority.MEDIUM : priority,
                dueAt,
                createdAt,
                createdAt
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
                id,
                authorId,
                assigneeId,
                title,
                description,
                status,
                priority,
                dueAt,
                createdAt,
                updatedAt
        );
    }

    public void assignTo(UserId newAssigneeId, Instant now) {
        ensureMutableForAssignment();
        this.assigneeId = requireNonNull(newAssigneeId, "newAssigneeId");
        this.updatedAt = requireNonNull(now, "now");

        if (this.status == TaskStatus.OPEN) {
            this.status = TaskStatus.IN_PROGRESS;
        }
    }

    public void markCompleted(Instant now) {
        if (this.status != TaskStatus.OPEN && this.status != TaskStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "task cannot be completed from status: " + this.status
            );
        }

        this.status = TaskStatus.DONE;
        this.updatedAt = requireNonNull(now, "now");
    }

    private void ensureMutableForAssignment() {
        if (this.status == TaskStatus.DONE || this.status == TaskStatus.CANCELLED) {
            throw new InvalidStateTransitionException(
                    "task cannot be reassigned from status: " + this.status
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
