package com.example.todo.application.command;

import com.example.todo.domain.task.TaskId;

import java.time.Instant;

public record CreateReminderCommand(
        TaskId taskId,
        Instant remindAt
) {
}