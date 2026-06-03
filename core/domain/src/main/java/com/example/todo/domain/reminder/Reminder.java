package com.example.todo.domain.reminder;

import com.example.todo.domain.task.TaskId;

import java.time.Instant;

public record Reminder(
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
    public Reminder {
        id = requireNonNull(id, "id");
        taskId = requireNonNull(taskId, "taskId");
        remindAt = requireNonNull(remindAt, "remindAt");
        status = requireNonNull(status, "status");
        createdAt = requireNonNull(createdAt, "createdAt");
        updatedAt = requireValidUpdatedAt(createdAt, updatedAt);
        if (nextAttemptAt != null && nextAttemptAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("nextAttemptAt must not be before createdAt");
        }
        if (processingStartedAt != null && processingStartedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("processingStartedAt must not be before createdAt");
        }
        if (processingOwner != null && processingOwner.isBlank()) {
            throw new IllegalArgumentException("processingOwner must not be blank");
        }
        if (deliveredAt != null && deliveredAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("deliveredAt must not be before createdAt");
        }
        if (deliveryAttempts < 0) {
            throw new IllegalArgumentException("deliveryAttempts must not be negative");
        }
        if (lastFailureReason != null && lastFailureReason.isBlank()) {
            throw new IllegalArgumentException("lastFailureReason must not be blank");
        }
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

    public Reminder markProcessing(String processorId, Instant updatedAt) {
        if (status != ReminderStatus.SCHEDULED && status != ReminderStatus.PROCESSING) {
            throw new IllegalStateException("reminder cannot be claimed for processing from status: " + status);
        }

        Instant actualUpdatedAt = requireValidUpdatedAt(updatedAt);
        return new Reminder(
                id,
                taskId,
                remindAt,
                ReminderStatus.PROCESSING,
                createdAt,
                actualUpdatedAt,
                nextAttemptAt,
                actualUpdatedAt,
                requireText(processorId, "processorId"),
                deliveredAt,
                deliveryAttempts,
                null
        );
    }

    public Reminder markDelivered(Instant deliveredAt) {
        if (status != ReminderStatus.PROCESSING) {
            throw new IllegalStateException("reminder cannot be marked as delivered from status: " + status);
        }

        Instant actualDeliveredAt = requireValidDeliveredAt(deliveredAt);
        return new Reminder(
                id,
                taskId,
                remindAt,
                ReminderStatus.DELIVERED,
                createdAt,
                actualDeliveredAt,
                nextAttemptAt,
                null,
                null,
                actualDeliveredAt,
                deliveryAttempts + 1,
                null
        );
    }

    public Reminder reschedule(Instant updatedAt, Instant nextAttemptAt, String failureReason) {
        if (status != ReminderStatus.PROCESSING) {
            throw new IllegalStateException("reminder cannot be rescheduled from status: " + status);
        }

        Instant actualUpdatedAt = requireValidUpdatedAt(updatedAt);
        Instant actualNextAttemptAt = requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (actualNextAttemptAt.isBefore(actualUpdatedAt)) {
            throw new IllegalArgumentException("nextAttemptAt must not be before the current processing time");
        }

        return new Reminder(
                id,
                taskId,
                remindAt,
                ReminderStatus.SCHEDULED,
                createdAt,
                actualUpdatedAt,
                actualNextAttemptAt,
                null,
                null,
                null,
                deliveryAttempts + 1,
                requireText(failureReason, "failureReason")
        );
    }

    public Reminder markFailed(Instant updatedAt, String failureReason) {
        if (status != ReminderStatus.PROCESSING) {
            throw new IllegalStateException("reminder cannot be marked as failed from status: " + status);
        }

        Instant actualUpdatedAt = requireValidUpdatedAt(updatedAt);
        return new Reminder(
                id,
                taskId,
                remindAt,
                ReminderStatus.FAILED,
                createdAt,
                actualUpdatedAt,
                nextAttemptAt,
                null,
                null,
                null,
                deliveryAttempts + 1,
                requireText(failureReason, "failureReason")
        );
    }

    private Instant requireValidUpdatedAt(Instant updatedAt) {
        Instant actualUpdatedAt = requireNonNull(updatedAt, "updatedAt");
        if (actualUpdatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (actualUpdatedAt.isBefore(this.updatedAt)) {
            throw new IllegalArgumentException("updatedAt must not move backwards");
        }
        return actualUpdatedAt;
    }

    private static Instant requireValidUpdatedAt(Instant createdAt, Instant updatedAt) {
        Instant actualUpdatedAt = requireNonNull(updatedAt, "updatedAt");
        if (actualUpdatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        return actualUpdatedAt;
    }

    private Instant requireValidDeliveredAt(Instant deliveredAt) {
        Instant actualDeliveredAt = requireNonNull(deliveredAt, "deliveredAt");
        if (actualDeliveredAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("deliveredAt must not be before createdAt");
        }
        if (actualDeliveredAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("updatedAt must not move backwards");
        }
        return actualDeliveredAt;
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

    public ReminderId getId() {
        return id;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public String getProcessingOwner() {
        return processingOwner;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }
}
