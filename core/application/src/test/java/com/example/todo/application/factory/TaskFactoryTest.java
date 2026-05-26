package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskFactoryTest {

    private final TaskFactory taskFactory = new TaskFactory();

    @Test
    void createShouldApplyDefaultsAndConsistentTimestamps() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        Instant dueAt = Instant.parse("2026-04-21T10:00:00Z");

        Task task = taskFactory.create(authorId, null, "Ship baseline", null, null, dueAt, now);

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
    void createShouldRespectProvidedAssigneeAndPriority() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        UserId assigneeId = userId("22222222-2222-2222-2222-222222222222");
        Instant now = Instant.parse("2026-04-20T10:00:00Z");

        Task task = taskFactory.create(
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
    void createShouldRejectBlankTitle() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> taskFactory.create(
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

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
