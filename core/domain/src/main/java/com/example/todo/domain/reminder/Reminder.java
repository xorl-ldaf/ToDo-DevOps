package com.example.todo.domain.reminder;

import com.example.todo.domain.task.TaskId;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class Reminder {
    private final ReminderId id;
    private final TaskId taskId;
    private final Instant remindAt;
    private ReminderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant sentAt;

    public Reminder(
            ReminderId id,
            TaskId taskId,
            Instant remindAt,
            ReminderStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.remindAt = Objects.requireNonNull(remindAt, "remindAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.sentAt = sentAt;

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public boolean isDueAt(Instant moment) {
        return status == ReminderStatus.PENDING && !remindAt.isAfter(moment);
    }

    public void markPublished(Instant now) {
        this.status = ReminderStatus.PUBLISHED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markSent(Instant now) {
        this.status = ReminderStatus.SENT;
        this.sentAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }
}