package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.ReminderId;

import java.time.Instant;

public interface FinalizeReminderDeliveryPort {
    boolean markDelivered(ReminderId reminderId, String processorId, Instant deliveredAt);

    boolean reschedule(ReminderId reminderId, String processorId, Instant processedAt, Instant nextAttemptAt, String failureReason);

    boolean markFailed(ReminderId reminderId, String processorId, Instant processedAt, String failureReason);
}
