package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.TaskJpaEntity;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;

public final class TaskPersistenceMapper {

    private TaskPersistenceMapper() {
    }

    public static TaskJpaEntity toJpa(Task task) {
        TaskJpaEntity entity = new TaskJpaEntity();
        entity.setId(task.getId().value());
        entity.setAuthorId(task.getAuthorId().value());
        entity.setAssigneeId(task.getAssigneeId().value());
        entity.setTitle(task.getTitle());
        entity.setDescription(task.getDescription());
        entity.setStatus(task.getStatus().name());
        entity.setPriority(task.getPriority().name());
        entity.setDueAt(task.getDueAt());
        entity.setCreatedAt(task.getCreatedAt());
        entity.setUpdatedAt(task.getUpdatedAt());
        return entity;
    }

    public static Task toDomain(TaskJpaEntity entity) {
        return new Task(
                new TaskId(entity.getId()),
                new UserId(entity.getAuthorId()),
                new UserId(entity.getAssigneeId()),
                entity.getTitle(),
                entity.getDescription(),
                TaskStatus.valueOf(entity.getStatus()),
                TaskPriority.valueOf(entity.getPriority()),
                entity.getDueAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}