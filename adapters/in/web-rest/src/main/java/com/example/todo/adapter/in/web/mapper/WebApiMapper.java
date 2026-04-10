package com.example.todo.adapter.in.web.mapper;

import com.example.todo.adapter.in.web.dto.AssignTaskRequest;
import com.example.todo.adapter.in.web.dto.CreateReminderRequest;
import com.example.todo.adapter.in.web.dto.CreateTaskRequest;
import com.example.todo.adapter.in.web.dto.ReminderResponse;
import com.example.todo.adapter.in.web.dto.TaskResponse;
import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

import java.util.UUID;

public final class WebApiMapper {

    private WebApiMapper() {
    }

    public static CreateTaskCommand toCommand(CreateTaskRequest request) {
        return new CreateTaskCommand(
                request.title(),
                request.description(),
                new UserId(request.authorId()),
                request.assigneeId() == null ? null : new UserId(request.assigneeId()),
                request.priority(),
                request.dueAt()
        );
    }

    public static AssignTaskCommand toCommand(UUID taskId, AssignTaskRequest request) {
        return new AssignTaskCommand(
                new TaskId(taskId),
                new UserId(request.assigneeId())
        );
    }

    public static CreateReminderCommand toCommand(UUID taskId, CreateReminderRequest request) {
        return new CreateReminderCommand(
                new TaskId(taskId),
                request.remindAt()
        );
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId().value(),
                task.getAuthorId().value(),
                task.getAssigneeId().value(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId().value(),
                user.getUsername(),
                user.getDisplayName(),
                user.getTelegramChatId() == null ? null : user.getTelegramChatId().value(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public static ReminderResponse toResponse(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId().value(),
                reminder.getTaskId().value(),
                reminder.getRemindAt(),
                reminder.getStatus(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt(),
                reminder.getSentAt()
        );
    }
}