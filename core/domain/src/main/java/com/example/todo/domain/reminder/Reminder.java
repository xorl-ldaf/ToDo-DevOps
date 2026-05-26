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
