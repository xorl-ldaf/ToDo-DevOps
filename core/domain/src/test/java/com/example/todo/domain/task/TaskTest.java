package com.example.todo.domain.task;

import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
