package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.TaskJpaEntity;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataTaskRepository extends JpaRepository<TaskJpaEntity, UUID> {
    Page<TaskJpaEntity> findByStatus(TaskStatus status, Pageable pageable);

    Page<TaskJpaEntity> findByPriority(TaskPriority priority, Pageable pageable);

    Page<TaskJpaEntity> findByStatusAndPriority(TaskStatus status, TaskPriority priority, Pageable pageable);
}
