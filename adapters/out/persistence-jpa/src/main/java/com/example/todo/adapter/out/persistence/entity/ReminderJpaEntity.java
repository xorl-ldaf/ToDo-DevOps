package com.example.todo.adapter.out.persistence.entity;

import com.example.todo.domain.reminder.ReminderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(
        name = "reminders",
        indexes = {
                @Index(name = "idx_reminders_task_id", columnList = "task_id"),
                @Index(name = "idx_reminders_status_next_attempt_at", columnList = "status, next_attempt_at")
        }
)
public class ReminderJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReminderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_owner", length = 128)
    private String processingOwner;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "last_failure_reason", length = 512)
    private String lastFailureReason;

    public ReminderJpaEntity() {
    }
}
