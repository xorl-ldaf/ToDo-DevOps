package com.example.todo.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID taskId,
        Instant remindAt,
        ReminderStatusDto status,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt
) {
}
