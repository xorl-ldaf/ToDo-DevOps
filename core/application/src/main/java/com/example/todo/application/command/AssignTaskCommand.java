package com.example.todo.application.command;

import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.user.UserId;

public record AssignTaskCommand(
        TaskId taskId,
        UserId assigneeId
) {
}