package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.TaskJpaEntity;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TaskPersistenceMapperTest {

    @Test
    void toJpaShouldMapEveryTaskField() {
        Task task = task();

        TaskJpaEntity entity = TaskPersistenceMapper.toJpa(task);

        assertThat(entity.getId()).isEqualTo(task.getId().value());
        assertThat(entity.getAuthorId()).isEqualTo(task.getAuthorId().value());
        assertThat(entity.getAssigneeId()).isEqualTo(task.getAssigneeId().value());
        assertThat(entity.getTitle()).isEqualTo(task.getTitle());
        assertThat(entity.getDescription()).isEqualTo(task.getDescription());
        assertThat(entity.getStatus()).isEqualTo(task.getStatus());
        assertThat(entity.getPriority()).isEqualTo(task.getPriority());
        assertThat(entity.getDueAt()).isEqualTo(task.getDueAt());
        assertThat(entity.getCreatedAt()).isEqualTo(task.getCreatedAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(task.getUpdatedAt());
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void toJpaShouldStoreEveryTaskStatusAsDatabaseValue(TaskStatus status) {
        TaskJpaEntity entity = TaskPersistenceMapper.toJpa(task(status, TaskPriority.MEDIUM));

        assertThat(entity.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(TaskPriority.class)
    void toJpaShouldStoreEveryTaskPriorityAsDatabaseValue(TaskPriority priority) {
        TaskJpaEntity entity = TaskPersistenceMapper.toJpa(task(TaskStatus.OPEN, priority));

        assertThat(entity.getPriority()).isEqualTo(priority);
    }

    @Test
    void toDomainShouldMapEveryTaskField() {
        TaskJpaEntity entity = taskEntity(TaskStatus.IN_PROGRESS, TaskPriority.HIGH);

        Task task = TaskPersistenceMapper.toDomain(entity);

        assertThat(task.getId().value()).isEqualTo(entity.getId());
        assertThat(task.getAuthorId().value()).isEqualTo(entity.getAuthorId());
        assertThat(task.getAssigneeId().value()).isEqualTo(entity.getAssigneeId());
        assertThat(task.getTitle()).isEqualTo(entity.getTitle());
        assertThat(task.getDescription()).isEqualTo(entity.getDescription());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.getDueAt()).isEqualTo(entity.getDueAt());
        assertThat(task.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(task.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void toDomainShouldReadEveryTaskStatusDatabaseValue(TaskStatus status) {
        Task task = TaskPersistenceMapper.toDomain(taskEntity(status, TaskPriority.MEDIUM));

        assertThat(task.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(TaskPriority.class)
    void toDomainShouldReadEveryTaskPriorityDatabaseValue(TaskPriority priority) {
        Task task = TaskPersistenceMapper.toDomain(taskEntity(TaskStatus.OPEN, priority));

        assertThat(task.getPriority()).isEqualTo(priority);
    }

    @Test
    void toDomainShouldFailClearlyForMissingTaskStatusDatabaseValue() {
        TaskJpaEntity entity = taskEntity(null, TaskPriority.HIGH);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TaskPersistenceMapper.toDomain(entity))
                .withMessageContaining("status");
    }

    @Test
    void toDomainShouldFailClearlyForMissingTaskPriorityDatabaseValue() {
        TaskJpaEntity entity = taskEntity(TaskStatus.IN_PROGRESS, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TaskPersistenceMapper.toDomain(entity))
                .withMessageContaining("priority");
    }

    private Task task() {
        return task(TaskStatus.IN_PROGRESS, TaskPriority.HIGH);
    }

    private Task task(TaskStatus status, TaskPriority priority) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-12T10:00:00Z");
        return Task.restore(
                new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                "Persist task",
                "mapper coverage",
                status,
                priority,
                Instant.parse("2026-05-13T09:00:00Z"),
                createdAt,
                updatedAt
        );
    }

    private TaskJpaEntity taskEntity(TaskStatus status, TaskPriority priority) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-12T10:00:00Z");
        TaskJpaEntity entity = new TaskJpaEntity();
        entity.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        entity.setAuthorId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        entity.setAssigneeId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        entity.setTitle("Persist task");
        entity.setDescription("mapper coverage");
        entity.setStatus(status);
        entity.setPriority(priority);
        entity.setDueAt(Instant.parse("2026-05-13T09:00:00Z"));
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
