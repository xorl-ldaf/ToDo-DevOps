package com.example.todo.adapter.out.persistence.entity;

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
        name = "reminder_scheduled_event_outbox",
        indexes = {
                @Index(name = "idx_reminder_scheduled_event_outbox_status_available_at", columnList = "status, available_at")
        }
)
public class ReminderScheduledEventOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "reminder_id", nullable = false)
    private UUID reminderId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReminderScheduledEventOutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_owner", length = 128)
    private String processingOwner;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "last_failure_reason", length = 512)
    private String lastFailureReason;
}
