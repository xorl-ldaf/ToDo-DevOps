package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStatePolicyTest {

    private final TaskStatePolicy taskStatePolicy = new TaskStatePolicy();

    @Test
    void assignShouldReassignOpenTaskAndMoveItToInProgress() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant assignedAt = createdAt.plusSeconds(300);
        UserId newAssigneeId = userId("33333333-3333-3333-3333-333333333333");
        Task task = task(TaskStatus.OPEN, createdAt, createdAt);

        Task assignedTask = taskStatePolicy.assign(task, newAssigneeId, assignedAt);

        assertEquals(newAssigneeId, assignedTask.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, assignedTask.getStatus());
        assertEquals(assignedAt, assignedTask.getUpdatedAt());
    }

    @Test
    void assignShouldKeepInProgressStatusWhenReassigning() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(120);
        Instant reassignedAt = updatedAt.plusSeconds(60);
        UserId newAssigneeId = userId("33333333-3333-3333-3333-333333333333");
        Task task = task(TaskStatus.IN_PROGRESS, createdAt, updatedAt);

        Task assignedTask = taskStatePolicy.assign(task, newAssigneeId, reassignedAt);

        assertEquals(newAssigneeId, assignedTask.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, assignedTask.getStatus());
        assertEquals(reassignedAt, assignedTask.getUpdatedAt());
    }

    @Test
    void assignShouldRejectIllegalTransitionFromDone() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = task(TaskStatus.DONE, createdAt, createdAt);

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> taskStatePolicy.assign(
                        task,
                        userId("33333333-3333-3333-3333-333333333333"),
                        createdAt.plusSeconds(60)
                )
        );

        assertEquals("task cannot be reassigned from status: DONE", exception.getMessage());
    }

    @Test
    void assignShouldRejectTimestampBeforeCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = task(TaskStatus.OPEN, createdAt, createdAt);

        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> taskStatePolicy.assign(
                        task,
                        userId("33333333-3333-3333-3333-333333333333"),
                        createdAt.minusSeconds(1)
                )
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
    }

    @Test
    void assignShouldRejectTimestampMovingBackwards() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(120);
        Task task = task(TaskStatus.OPEN, createdAt, updatedAt);

        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> taskStatePolicy.assign(
                        task,
                        userId("33333333-3333-3333-3333-333333333333"),
                        updatedAt.minusSeconds(1)
                )
        );

        assertEquals("updatedAt must not move backwards", exception.getMessage());
    }

    @Test
    void markCompletedShouldMoveOpenTaskToDone() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant completedAt = createdAt.plusSeconds(600);
        Task task = task(TaskStatus.OPEN, createdAt, createdAt);

        Task completedTask = taskStatePolicy.markCompleted(task, completedAt);

        assertEquals(TaskStatus.DONE, completedTask.getStatus());
        assertEquals(completedAt, completedTask.getUpdatedAt());
    }

    @Test
    void markCompletedShouldRejectIllegalTransitionFromCancelled() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = task(TaskStatus.CANCELLED, createdAt, createdAt);

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> taskStatePolicy.markCompleted(task, createdAt.plusSeconds(60))
        );

        assertEquals("task cannot be completed from status: CANCELLED", exception.getMessage());
    }

    @Test
    void markCompletedShouldRejectTimestampBeforeCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = task(TaskStatus.OPEN, createdAt, createdAt);

        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> taskStatePolicy.markCompleted(task, createdAt.minusSeconds(1))
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
    }

    private Task task(TaskStatus status, Instant createdAt, Instant updatedAt) {
        return new Task(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Open task",
                "assign",
                status,
                TaskPriority.MEDIUM,
                null,
                createdAt,
                updatedAt
        );
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
