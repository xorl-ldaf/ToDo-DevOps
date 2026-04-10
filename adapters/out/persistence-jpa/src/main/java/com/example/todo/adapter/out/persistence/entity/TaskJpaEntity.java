package com.example.todo.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tasks")
public class TaskJpaEntity {
    @Id
    private UUID id;
    private UUID authorId;
    private UUID assigneeId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Instant dueAt;
    private Instant createdAt;
    private Instant updatedAt;

    public TaskJpaEntity() {
    }
}