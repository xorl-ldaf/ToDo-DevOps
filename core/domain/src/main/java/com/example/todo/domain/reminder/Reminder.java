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
    private Instant nextAttemptAt;
    private Instant processingStartedAt;
    private String processingOwner;
    private Instant deliveredAt;
    private int deliveryAttempts;
    private String lastFailureReason;

    private Reminder(
            ReminderId id,
            TaskId taskId,
            Instant remindAt,
            ReminderStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant nextAttemptAt,
            Instant processingStartedAt,
            String processingOwner,
            Instant deliveredAt,
            int deliveryAttempts,
            String lastFailureReason
    ) {
        this.id = requireNonNull(id, "id");
        this.taskId = requireNonNull(taskId, "taskId");
        this.remindAt = requireNonNull(remindAt, "remindAt");
        this.status = requireNonNull(status, "status");
        this.createdAt = requireNonNull(createdAt, "createdAt");
        this.updatedAt = requireNonNull(updatedAt, "updatedAt");
        this.nextAttemptAt = requireNonNull(nextAttemptAt, "nextAttemptAt");
        this.processingStartedAt = processingStartedAt;
        this.processingOwner = normalizeBlank(processingOwner);
        this.deliveredAt = deliveredAt;
        this.deliveryAttempts = requireNonNegative(deliveryAttempts, "deliveryAttempts");
        this.lastFailureReason = normalizeBlank(lastFailureReason);

        if (updatedAt.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt must not be before createdAt");
        }
        if (nextAttemptAt.isBefore(createdAt)) {
            throw new DomainValidationException("nextAttemptAt must not be before createdAt");
        }
        if (processingStartedAt != null && processingStartedAt.isBefore(createdAt)) {
            throw new DomainValidationException("processingStartedAt must not be before createdAt");
        }
        if (deliveredAt != null && deliveredAt.isBefore(createdAt)) {
            throw new DomainValidationException("deliveredAt must not be before createdAt");
        }
        if (status == ReminderStatus.PROCESSING && processingStartedAt == null) {
            throw new DomainValidationException("processingStartedAt must be set for PROCESSING reminders");
        }
        if (status == ReminderStatus.PROCESSING && this.processingOwner == null) {
            throw new DomainValidationException("processingOwner must be set for PROCESSING reminders");
        }
        if (status != ReminderStatus.PROCESSING && processingStartedAt != null) {
            throw new DomainValidationException("processingStartedAt must be null outside PROCESSING status");
        }
        if (status != ReminderStatus.PROCESSING && this.processingOwner != null) {
            throw new DomainValidationException("processingOwner must be null outside PROCESSING status");
        }
        if (status == ReminderStatus.DELIVERED && deliveredAt == null) {
            throw new DomainValidationException("deliveredAt must be set for DELIVERED reminders");
        }
        if (status != ReminderStatus.DELIVERED && deliveredAt != null) {
            throw new DomainValidationException("deliveredAt must be null outside DELIVERED status");
        }
        if (status == ReminderStatus.FAILED && this.lastFailureReason == null) {
            throw new DomainValidationException("lastFailureReason must be set for FAILED reminders");
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
                ReminderStatus.SCHEDULED,
                createdAt,
                createdAt,
                actualRemindAt,
                null,
                null,
                null,
                0,
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
            Instant nextAttemptAt,
            Instant processingStartedAt,
            String processingOwner,
            Instant deliveredAt,
            int deliveryAttempts,
            String lastFailureReason
    ) {
        return new Reminder(
                id,
                taskId,
                remindAt,
                status,
                createdAt,
                updatedAt,
                nextAttemptAt,
                processingStartedAt,
                processingOwner,
                deliveredAt,
                deliveryAttempts,
                lastFailureReason
        );
    }

    public boolean isDueAt(Instant moment) {
        Instant actualMoment = requireNonNull(moment, "moment");
        return status == ReminderStatus.SCHEDULED && !nextAttemptAt.isAfter(actualMoment);
    }

    public void markProcessing(String owner, Instant now) {
        if (this.status != ReminderStatus.SCHEDULED && this.status != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be claimed for processing from status: " + this.status
            );
        }

        Instant actualNow = requireValidUpdateTime(now);
        this.status = ReminderStatus.PROCESSING;
        this.processingOwner = requireText(owner, "owner");
        this.processingStartedAt = actualNow;
        this.updatedAt = actualNow;
        this.lastFailureReason = null;
    }

    public void markDelivered(Instant now) {
        if (this.status != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be marked as delivered from status: " + this.status
            );
        }

        Instant actualNow = requireValidDeliveredTime(now);
        this.status = ReminderStatus.DELIVERED;
        this.deliveredAt = actualNow;
        this.processingOwner = null;
        this.processingStartedAt = null;
        this.updatedAt = actualNow;
        this.deliveryAttempts++;
        this.lastFailureReason = null;
    }

    public void reschedule(Instant now, Instant nextAttemptAt, String failureReason) {
        if (this.status != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be rescheduled from status: " + this.status
            );
        }

        Instant actualNow = requireValidUpdateTime(now);
        Instant actualNextAttemptAt = requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (actualNextAttemptAt.isBefore(actualNow)) {
            throw new DomainValidationException("nextAttemptAt must not be before the current processing time");
        }

        this.status = ReminderStatus.SCHEDULED;
        this.nextAttemptAt = actualNextAttemptAt;
        this.processingOwner = null;
        this.processingStartedAt = null;
        this.updatedAt = actualNow;
        this.deliveryAttempts++;
        this.lastFailureReason = requireText(failureReason, "failureReason");
    }

    public void markFailed(Instant now, String failureReason) {
        if (this.status != ReminderStatus.PROCESSING) {
            throw new InvalidStateTransitionException(
                    "reminder cannot be marked as failed from status: " + this.status
            );
        }

        Instant actualNow = requireValidUpdateTime(now);
        this.status = ReminderStatus.FAILED;
        this.processingOwner = null;
        this.processingStartedAt = null;
        this.updatedAt = actualNow;
        this.deliveryAttempts++;
        this.lastFailureReason = requireText(failureReason, "failureReason");
    }

    private Instant requireValidUpdateTime(Instant now) {
        Instant actualNow = requireNonNull(now, "now");
        if (actualNow.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt must not be before createdAt");
        }
        return actualNow;
    }

    private Instant requireValidDeliveredTime(Instant now) {
        Instant actualNow = requireNonNull(now, "now");
        if (actualNow.isBefore(createdAt)) {
            throw new DomainValidationException("deliveredAt must not be before createdAt");
        }
        return actualNow;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " must not be null");
        }
        return value;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new DomainValidationException(fieldName + " must not be negative");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = requireNonNull(value, fieldName);
        if (actualValue.isBlank()) {
            throw new DomainValidationException(fieldName + " must not be blank");
        }
        return actualValue;
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
