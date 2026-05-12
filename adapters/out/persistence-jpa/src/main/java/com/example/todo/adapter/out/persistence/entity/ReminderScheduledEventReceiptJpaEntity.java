package com.example.todo.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "reminder_scheduled_event_receipts",
        indexes = {
                @Index(name = "idx_reminder_scheduled_event_receipts_consumed_at", columnList = "consumed_at")
        }
)
public class ReminderScheduledEventReceiptJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "reminder_id", nullable = false)
    private UUID reminderId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "event_version", nullable = false, length = 32)
    private String eventVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    @Column(name = "kafka_partition", nullable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false)
    private long kafkaOffset;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;
}
