package com.example.todo.adapter.in.web.mapper;

import com.example.todo.adapter.in.web.dto.AssignTaskRequest;
import com.example.todo.adapter.in.web.dto.CreateReminderRequest;
import com.example.todo.adapter.in.web.dto.CreateTaskRequest;
import com.example.todo.adapter.in.web.dto.CreateUserRequest;
import com.example.todo.adapter.in.web.dto.ReminderResponse;
import com.example.todo.adapter.in.web.dto.ReminderStatusDto;
import com.example.todo.adapter.in.web.dto.TaskPriorityDto;
import com.example.todo.adapter.in.web.dto.TaskResponse;
import com.example.todo.adapter.in.web.dto.TaskStatusDto;
import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.application.command.CreateUserCommand;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebApiMapperTest {
    private static final UUID TASK_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID AUTHOR_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ASSIGNEE_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REMINDER_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant CREATED_AT = Instant.parse("2026-05-12T09:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-05-12T10:00:00Z");
    private static final Instant DUE_AT = Instant.parse("2026-05-13T09:00:00Z");
    private static final Instant REMIND_AT = Instant.parse("2026-05-13T08:00:00Z");

    @Test
    void createTaskRequestShouldMapToCommandWithoutApplyingDefaults() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Write mapper tests",
                null,
                AUTHOR_UUID,
                null,
                null,
                null
        );

        CreateTaskCommand command = WebApiMapper.toCommand(request);

        assertThat(command.title()).isEqualTo("Write mapper tests");
        assertThat(command.description()).isNull();
        assertThat(command.authorId()).isEqualTo(new UserId(AUTHOR_UUID));
        assertThat(command.assigneeId()).isNull();
        assertThat(command.priority()).isNull();
        assertThat(command.dueAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(TaskPriorityDto.class)
    void createTaskRequestShouldMapEveryPriority(TaskPriorityDto priority) {
        CreateTaskRequest request = new CreateTaskRequest(
                "Write mapper tests",
                "coverage",
                AUTHOR_UUID,
                ASSIGNEE_UUID,
                priority,
                DUE_AT
        );

        CreateTaskCommand command = WebApiMapper.toCommand(request);

        assertThat(command.priority()).isEqualTo(TaskPriority.valueOf(priority.name()));
    }

    @Test
    void assignTaskRequestShouldMapToCommand() {
        AssignTaskCommand command = WebApiMapper.toCommand(TASK_UUID, new AssignTaskRequest(ASSIGNEE_UUID));

        assertThat(command.taskId()).isEqualTo(new TaskId(TASK_UUID));
        assertThat(command.assigneeId()).isEqualTo(new UserId(ASSIGNEE_UUID));
    }

    @Test
    void createReminderRequestShouldMapToCommand() {
        CreateReminderCommand command = WebApiMapper.toCommand(TASK_UUID, new CreateReminderRequest(REMIND_AT));

        assertThat(command.taskId()).isEqualTo(new TaskId(TASK_UUID));
        assertThat(command.remindAt()).isEqualTo(REMIND_AT);
    }

    @Test
    void createUserRequestShouldMapToCommand() {
        CreateUserCommand command = WebApiMapper.toCommand(new CreateUserRequest("alice", "Alice", 123456789L));

        assertThat(command.username()).isEqualTo("alice");
        assertThat(command.displayName()).isEqualTo("Alice");
        assertThat(command.telegramChatId()).isEqualTo(new TelegramChatId(123456789L));
    }

    @Test
    void createUserRequestShouldPreserveMissingTelegramChatId() {
        CreateUserCommand command = WebApiMapper.toCommand(new CreateUserRequest("alice", "Alice", null));

        assertThat(command.telegramChatId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void taskResponseShouldMapEveryStatus(TaskStatus status) {
        TaskResponse response = WebApiMapper.toResponse(task(status, TaskPriority.HIGH));

        assertThat(response.status()).isEqualTo(TaskStatusDto.valueOf(status.name()));
    }

    @ParameterizedTest
    @EnumSource(TaskPriority.class)
    void taskResponseShouldMapEveryPriority(TaskPriority priority) {
        TaskResponse response = WebApiMapper.toResponse(task(TaskStatus.IN_PROGRESS, priority));

        assertThat(response.priority()).isEqualTo(TaskPriorityDto.valueOf(priority.name()));
    }

    @Test
    void taskResponseShouldMapFields() {
        Task task = task(TaskStatus.IN_PROGRESS, TaskPriority.HIGH);

        TaskResponse response = WebApiMapper.toResponse(task);

        assertThat(response.id()).isEqualTo(TASK_UUID);
        assertThat(response.authorId()).isEqualTo(AUTHOR_UUID);
        assertThat(response.assigneeId()).isEqualTo(ASSIGNEE_UUID);
        assertThat(response.title()).isEqualTo("Persist task");
        assertThat(response.description()).isEqualTo("mapper coverage");
        assertThat(response.dueAt()).isEqualTo(DUE_AT);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void userResponseShouldMapFieldsAndPreserveMissingTelegramChatId() {
        User user = new User(
                new UserId(AUTHOR_UUID),
                "alice",
                "Alice",
                null,
                CREATED_AT,
                UPDATED_AT
        );

        UserResponse response = WebApiMapper.toResponse(user);

        assertThat(response.id()).isEqualTo(AUTHOR_UUID);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.telegramChatId()).isNull();
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @ParameterizedTest
    @EnumSource(ReminderStatus.class)
    void reminderResponseShouldMapEveryStatus(ReminderStatus status) {
        ReminderResponse response = WebApiMapper.toResponse(reminder(status));

        assertThat(response.status()).isEqualTo(ReminderStatusDto.valueOf(status.name()));
    }

    @Test
    void reminderResponseShouldMapFields() {
        Reminder reminder = reminder(ReminderStatus.DELIVERED);

        ReminderResponse response = WebApiMapper.toResponse(reminder);

        assertThat(response.id()).isEqualTo(REMINDER_UUID);
        assertThat(response.taskId()).isEqualTo(TASK_UUID);
        assertThat(response.remindAt()).isEqualTo(REMIND_AT);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(response.deliveredAt()).isEqualTo(UPDATED_AT);
    }

    private static Task task(TaskStatus status, TaskPriority priority) {
        return new Task(
                new TaskId(TASK_UUID),
                new UserId(AUTHOR_UUID),
                new UserId(ASSIGNEE_UUID),
                "Persist task",
                "mapper coverage",
                status,
                priority,
                DUE_AT,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static Reminder reminder(ReminderStatus status) {
        return Reminder.restore(
                new ReminderId(REMINDER_UUID),
                new TaskId(TASK_UUID),
                REMIND_AT,
                status,
                CREATED_AT,
                UPDATED_AT,
                REMIND_AT,
                null,
                null,
                status == ReminderStatus.DELIVERED ? UPDATED_AT : null,
                status == ReminderStatus.SCHEDULED ? 0 : 1,
                null
        );
    }
}
