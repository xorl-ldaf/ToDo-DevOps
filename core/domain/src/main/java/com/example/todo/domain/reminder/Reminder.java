package com.example.todo.domain.reminder;

import com.example.todo.domain.shared.exception.DomainValidationException;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.TaskId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Reminder {
    private final ReminderId id;
    private final TaskId taskId;
    private final Instant remindAt;
    private ReminderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant sentAt;

    private Reminder(
            ReminderId id,
            TaskId taskId,
            Instant remindAt,
            ReminderStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt
    ) {
        this.id = requireNonNull(id, "id");
        this.taskId = requireNonNull(taskId, "taskId");
        this.remindAt = requireNonNull(remindAt, "remindAt");
        this.status = requireNonNull(status, "status");
        this.createdAt = requireNonNull(createdAt, "createdAt");
        this.updatedAt = requireNonNull(updatedAt, "updatedAt");
        this.sentAt = sentAt;

        if (updatedAt.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt must not be before createdAt");
        }
        if (sentAt != null && sentAt.isBefore(createdAt)) {
            throw new DomainValidationException("sentAt must not be before createdAt");
        }
    }

    public static Reminder schedule(TaskId taskId, Instant remindAt, Instant now) {
        Instant createdAt = requireNonNull(now, "now");
        Instant actualRemindAt = requireNonNull(remindAt, "remindAt");

        if (actualRemindAt.isBefore(createdAt)) {
            throw new DomainValidationException("remindAt must not be in the past");
        }

        return new Reminder(
                ReminderId.newId(),
                taskId,
                actualRemindAt,
                ReminderStatus.PENDING,
                createdAt,
                createdAt,
                null
        );
    }

    public static Reminder restore(
            ReminderId id,
            TaskId taskId,
            Instant remindAt,
            ReminderStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt
    ) {
        return new Reminder(
                id,
                taskId,
                remindAt,
                status,
                createdAt,
                updatedAt,
                sentAt
        );
    }

    public boolean isDueAt(Instant moment) {
        Instant actualMoment = requireNonNull(moment, "moment");
        return status == ReminderStatus.PENDING && !remindAt.isAfter(actualMoment);
    }

    public void markPublished(Instant now) {
        if (this.status != ReminderStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be published from status: " + this.status
            );
        }

        Instant actualNow = requireValidUpdateTime(now);
        this.status = ReminderStatus.PUBLISHED;
        this.updatedAt = actualNow;
    }

    public void markSent(Instant now) {
        if (this.status != ReminderStatus.PUBLISHED) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be marked as sent from status: " + this.status
            );
        }

        Instant actualNow = requireValidSentTime(now);
        this.status = ReminderStatus.SENT;
        this.sentAt = actualNow;
        this.updatedAt = actualNow;
    }

    private Instant requireValidUpdateTime(Instant now) {
        Instant actualNow = requireNonNull(now, "now");
        if (actualNow.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt must not be before createdAt");
        }
        return actualNow;
    }

    private Instant requireValidSentTime(Instant now) {
        Instant actualNow = requireNonNull(now, "now");
        if (actualNow.isBefore(createdAt)) {
            throw new DomainValidationException("sentAt must not be before createdAt");
        }
        return actualNow;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
