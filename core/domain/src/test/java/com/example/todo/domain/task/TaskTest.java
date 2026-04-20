package com.example.todo.domain.task;

import com.example.todo.domain.shared.exception.DomainValidationException;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void createNewShouldApplyDefaultsAndConsistentTimestamps() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        Instant dueAt = Instant.parse("2026-04-21T10:00:00Z");

        Task task = Task.createNew(authorId, null, "Ship baseline", null, null, dueAt, now);

        assertNotNull(task.getId());
        assertEquals(authorId, task.getAuthorId());
        assertEquals(authorId, task.getAssigneeId());
        assertEquals("Ship baseline", task.getTitle());
        assertEquals("", task.getDescription());
        assertEquals(TaskStatus.OPEN, task.getStatus());
        assertEquals(TaskPriority.MEDIUM, task.getPriority());
        assertEquals(dueAt, task.getDueAt());
        assertEquals(now, task.getCreatedAt());
        assertEquals(now, task.getUpdatedAt());
    }

    @Test
    void createNewShouldRespectProvidedAssigneeAndPriority() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        UserId assigneeId = userId("22222222-2222-2222-2222-222222222222");
        Instant now = Instant.parse("2026-04-20T10:00:00Z");

        Task task = Task.createNew(
                authorId,
                assigneeId,
                "Handle escalation",
                "Investigate production issue",
                TaskPriority.CRITICAL,
                null,
                now
        );

        assertEquals(assigneeId, task.getAssigneeId());
        assertEquals("Investigate production issue", task.getDescription());
        assertEquals(TaskPriority.CRITICAL, task.getPriority());
        assertNull(task.getDueAt());
    }

    @Test
    void createNewShouldRejectBlankTitle() {
        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Task.createNew(
                        userId("11111111-1111-1111-1111-111111111111"),
                        null,
                        "   ",
                        "desc",
                        null,
                        null,
                        Instant.parse("2026-04-20T10:00:00Z")
                )
        );

        assertEquals("title must not be blank", exception.getMessage());
    }

    @Test
    void restoreShouldRejectUpdatedAtBeforeCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = createdAt.minusSeconds(1);

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Task.restore(
                        taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        userId("11111111-1111-1111-1111-111111111111"),
                        userId("22222222-2222-2222-2222-222222222222"),
                        "Broken task",
                        "invalid timestamps",
                        TaskStatus.OPEN,
                        TaskPriority.HIGH,
                        null,
                        createdAt,
                        updatedAt
                )
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
    }

    @Test
    void assignToShouldReassignOpenTaskAndMoveItToInProgress() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant assignedAt = createdAt.plusSeconds(300);
        UserId newAssigneeId = userId("33333333-3333-3333-3333-333333333333");
        Task task = openTask(createdAt);

        task.assignTo(newAssigneeId, assignedAt);

        assertEquals(newAssigneeId, task.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(assignedAt, task.getUpdatedAt());
    }

    @Test
    void assignToShouldKeepInProgressStatusWhenReassigning() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(120);
        Task task = Task.restore(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "In progress task",
                "reassign",
                TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH,
                null,
                createdAt,
                updatedAt
        );
        Instant reassignedAt = updatedAt.plusSeconds(60);
        UserId newAssigneeId = userId("33333333-3333-3333-3333-333333333333");

        task.assignTo(newAssigneeId, reassignedAt);

        assertEquals(newAssigneeId, task.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(reassignedAt, task.getUpdatedAt());
    }

    @Test
    void assignToShouldRejectIllegalTransitionFromDone() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = Task.restore(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Done task",
                "cannot reassign",
                TaskStatus.DONE,
                TaskPriority.HIGH,
                null,
                createdAt,
                createdAt
        );

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> task.assignTo(userId("33333333-3333-3333-3333-333333333333"), createdAt.plusSeconds(60))
        );

        assertEquals("task cannot be reassigned from status: DONE", exception.getMessage());
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(userId("22222222-2222-2222-2222-222222222222"), task.getAssigneeId());
        assertEquals(createdAt, task.getUpdatedAt());
    }

    @Test
    void assignToShouldRejectTimestampBeforeCreatedAtWithoutMutatingState() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        UserId originalAssigneeId = userId("22222222-2222-2222-2222-222222222222");
        Task task = Task.restore(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                originalAssigneeId,
                "Task",
                "description",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                createdAt,
                createdAt
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> task.assignTo(userId("33333333-3333-3333-3333-333333333333"), createdAt.minusSeconds(1))
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
        assertEquals(TaskStatus.OPEN, task.getStatus());
        assertEquals(originalAssigneeId, task.getAssigneeId());
        assertEquals(createdAt, task.getUpdatedAt());
    }

    @Test
    void markCompletedShouldMoveTaskToDone() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Instant completedAt = createdAt.plusSeconds(600);
        Task task = openTask(createdAt);

        task.markCompleted(completedAt);

        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(completedAt, task.getUpdatedAt());
    }

    @Test
    void markCompletedShouldRejectIllegalTransitionFromCancelled() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = Task.restore(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Cancelled task",
                "cannot complete",
                TaskStatus.CANCELLED,
                TaskPriority.MEDIUM,
                null,
                createdAt,
                createdAt
        );

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> task.markCompleted(createdAt.plusSeconds(60))
        );

        assertEquals("task cannot be completed from status: CANCELLED", exception.getMessage());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals(createdAt, task.getUpdatedAt());
    }

    @Test
    void markCompletedShouldRejectTimestampBeforeCreatedAtWithoutMutatingState() {
        Instant createdAt = Instant.parse("2026-04-20T10:00:00Z");
        Task task = openTask(createdAt);

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> task.markCompleted(createdAt.minusSeconds(1))
        );

        assertEquals("updatedAt must not be before createdAt", exception.getMessage());
        assertEquals(TaskStatus.OPEN, task.getStatus());
        assertEquals(createdAt, task.getUpdatedAt());
    }

    private Task openTask(Instant createdAt) {
        return Task.restore(
                taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Open task",
                "domain baseline",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                createdAt,
                createdAt
        );
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
