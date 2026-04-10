package com.example.todo.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "reminders")
public class ReminderJpaEntity {
    @Id
    private UUID id;
    private UUID taskId;
    private Instant remindAt;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant sentAt;

    public ReminderJpaEntity() {
    }
}