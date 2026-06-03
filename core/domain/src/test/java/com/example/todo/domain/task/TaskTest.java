package com.example.todo.domain.task;

import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskTest {

    @Test
    void constructorShouldStoreTaskData() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        UserId assigneeId = userId("22222222-2222-2222-2222-222222222222");
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-20T11:00:00Z");
        Instant dueAt = Instant.parse("2026-04-21T10:00:00Z");

        Task task = new Task(
                taskId,
                authorId,
                assigneeId,
                "Ship baseline",
                "Review release checklist",
                TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH,
                dueAt,
                createdAt,
                updatedAt
        );

        assertEquals(taskId, task.getId());
        assertEquals(authorId, task.getAuthorId());
        assertEquals(assigneeId, task.getAssigneeId());
        assertEquals("Ship baseline", task.getTitle());
        assertEquals("Review release checklist", task.getDescription());
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(dueAt, task.getDueAt());
        assertEquals(createdAt, task.getCreatedAt());
        assertEquals(updatedAt, task.getUpdatedAt());
    }

    @Test
    void assignToShouldReturnTaskWithNewAssigneeAndUpdatedTimestamp() {
        UserId originalAssigneeId = userId("22222222-2222-2222-2222-222222222222");
        UserId newAssigneeId = userId("33333333-3333-3333-3333-333333333333");
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-20T11:00:00Z");
        Task task = new Task(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                originalAssigneeId,
                "Ship baseline",
                "Review release checklist",
                TaskStatus.OPEN,
                TaskPriority.HIGH,
                null,
                createdAt,
                createdAt
        );

        Task assignedTask = task.assignTo(newAssigneeId, updatedAt);

        assertEquals(originalAssigneeId, task.getAssigneeId());
        assertEquals(TaskStatus.OPEN, task.getStatus());
        assertEquals(newAssigneeId, assignedTask.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, assignedTask.getStatus());
        assertEquals(updatedAt, assignedTask.getUpdatedAt());
    }

    @Test
    void createNewShouldRejectBlankTitle() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Task.createNew(
                        userId("11111111-1111-1111-1111-111111111111"),
                        null,
                        " ",
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-20T10:00:00Z")
                )
        );

        assertEquals("title must not be blank", exception.getMessage());
    }

    @Test
    void constructorShouldRejectMissingTimestamps() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");

        IllegalArgumentException createdAtException = assertThrows(
                IllegalArgumentException.class,
                () -> new Task(
                        taskId,
                        authorId,
                        authorId,
                        "Ship baseline",
                        "desc",
                        TaskStatus.OPEN,
                        TaskPriority.MEDIUM,
                        null,
                        null,
                        Instant.parse("2026-04-20T10:00:00Z")
                )
        );
        IllegalArgumentException updatedAtException = assertThrows(
                IllegalArgumentException.class,
                () -> new Task(
                        taskId,
                        authorId,
                        authorId,
                        "Ship baseline",
                        "desc",
                        TaskStatus.OPEN,
                        TaskPriority.MEDIUM,
                        null,
                        Instant.parse("2026-04-20T10:00:00Z"),
                        null
                )
        );

        assertEquals("createdAt must not be null", createdAtException.getMessage());
        assertEquals("updatedAt must not be null", updatedAtException.getMessage());
    }

    @Test
    void constructorShouldRejectUpdatedAtBeforeCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Task(
                        taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        userId("11111111-1111-1111-1111-111111111111"),
                        userId("22222222-2222-2222-2222-222222222222"),
                        "Ship baseline",
                        "desc",
                        TaskStatus.OPEN,
                        TaskPriority.MEDIUM,
                        null,
                        createdAt,
                        createdAt.minusSeconds(1)
                )
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
    }

    @Test
    void assignToShouldRejectTerminalTaskAndBackwardTimestamp() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-20T11:00:00Z");
        Task doneTask = new Task(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Ship baseline",
                "desc",
                TaskStatus.DONE,
                TaskPriority.MEDIUM,
                null,
                createdAt,
                updatedAt
        );
        Task openTask = new Task(
                taskId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Open baseline",
                "desc",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                createdAt,
                updatedAt
        );

        IllegalStateException stateException = assertThrows(
                IllegalStateException.class,
                () -> doneTask.assignTo(userId("33333333-3333-3333-3333-333333333333"), updatedAt.plusSeconds(1))
        );
        IllegalArgumentException timeException = assertThrows(
                IllegalArgumentException.class,
                () -> openTask.assignTo(userId("33333333-3333-3333-3333-333333333333"), updatedAt.minusSeconds(1))
        );

        assertEquals("task cannot be reassigned from status: DONE", stateException.getMessage());
        assertEquals("updatedAt must not move backwards", timeException.getMessage());
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
