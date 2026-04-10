package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.reminder.ReminderStatus;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID taskId,
        Instant remindAt,
        ReminderStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt
) {
}