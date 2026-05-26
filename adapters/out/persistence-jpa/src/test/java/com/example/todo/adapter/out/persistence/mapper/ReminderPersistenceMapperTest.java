package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ReminderPersistenceMapperTest {

    @Test
    void toJpaShouldMapEveryProcessingField() {
        Reminder reminder = processingReminder();

        ReminderJpaEntity entity = ReminderPersistenceMapper.toJpa(reminder);

        assertThat(entity.getId()).isEqualTo(reminder.getId().value());
        assertThat(entity.getTaskId()).isEqualTo(reminder.getTaskId().value());
        assertThat(entity.getRemindAt()).isEqualTo(reminder.getRemindAt());
        assertThat(entity.getStatus()).isEqualTo(reminder.getStatus().name());
        assertThat(entity.getCreatedAt()).isEqualTo(reminder.getCreatedAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(reminder.getUpdatedAt());
        assertThat(entity.getNextAttemptAt()).isEqualTo(reminder.getNextAttemptAt());
        assertThat(entity.getProcessingStartedAt()).isEqualTo(reminder.getProcessingStartedAt());
        assertThat(entity.getProcessingOwner()).isEqualTo(reminder.getProcessingOwner());
        assertThat(entity.getDeliveredAt()).isEqualTo(reminder.getDeliveredAt());
        assertThat(entity.getDeliveryAttempts()).isEqualTo(reminder.getDeliveryAttempts());
        assertThat(entity.getLastFailureReason()).isEqualTo(reminder.getLastFailureReason());
    }

    @ParameterizedTest
    @EnumSource(ReminderStatus.class)
    void toJpaShouldStoreEveryReminderStatusAsDatabaseValue(ReminderStatus status) {
        ReminderJpaEntity entity = ReminderPersistenceMapper.toJpa(reminder(status));

        assertThat(entity.getStatus()).isEqualTo(status.name());
    }

    @Test
    void toDomainShouldMapEveryDeliveredField() {
        ReminderJpaEntity entity = deliveredReminderEntity(ReminderStatus.DELIVERED.name());

        Reminder reminder = ReminderPersistenceMapper.toDomain(entity);

        assertThat(reminder.getId().value()).isEqualTo(entity.getId());
        assertThat(reminder.getTaskId().value()).isEqualTo(entity.getTaskId());
        assertThat(reminder.getRemindAt()).isEqualTo(entity.getRemindAt());
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(reminder.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(reminder.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
        assertThat(reminder.getNextAttemptAt()).isEqualTo(entity.getNextAttemptAt());
        assertThat(reminder.getProcessingStartedAt()).isEqualTo(entity.getProcessingStartedAt());
        assertThat(reminder.getProcessingOwner()).isEqualTo(entity.getProcessingOwner());
        assertThat(reminder.getDeliveredAt()).isEqualTo(entity.getDeliveredAt());
        assertThat(reminder.getDeliveryAttempts()).isEqualTo(entity.getDeliveryAttempts());
        assertThat(reminder.getLastFailureReason()).isEqualTo(entity.getLastFailureReason());
    }

    @ParameterizedTest
    @EnumSource(ReminderStatus.class)
    void toDomainShouldReadEveryReminderStatusDatabaseValue(ReminderStatus status) {
        Reminder reminder = ReminderPersistenceMapper.toDomain(reminderEntity(status.name()));

        assertThat(reminder.getStatus()).isEqualTo(status);
    }

    @Test
    void toDomainShouldFailClearlyForUnknownReminderStatusDatabaseValue() {
        ReminderJpaEntity entity = reminderEntity("UNKNOWN");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReminderPersistenceMapper.toDomain(entity))
                .withMessage("Unknown reminder status persistence value: UNKNOWN");
    }

    @Test
    void toDomainShouldFailClearlyForMissingReminderStatusDatabaseValue() {
        ReminderJpaEntity entity = reminderEntity(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReminderPersistenceMapper.toDomain(entity))
                .withMessage("Reminder status persistence value must not be null");
    }

    private Reminder processingReminder() {
        return reminder(ReminderStatus.PROCESSING);
    }

    private Reminder reminder(ReminderStatus status) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        Instant processingStartedAt = Instant.parse("2026-05-12T10:01:00Z");
        return Reminder.restore(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new TaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                Instant.parse("2026-05-12T10:00:00Z"),
                status,
                createdAt,
                processingStartedAt,
                Instant.parse("2026-05-12T10:00:00Z"),
                processingStartedAt,
                "worker-1",
                null,
                1,
                "previous timeout"
        );
    }

    private ReminderJpaEntity deliveredReminderEntity(String status) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        Instant deliveredAt = Instant.parse("2026-05-12T10:05:00Z");
        ReminderJpaEntity entity = reminderEntity(status);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(deliveredAt);
        entity.setProcessingStartedAt(null);
        entity.setProcessingOwner(null);
        entity.setDeliveredAt(deliveredAt);
        entity.setDeliveryAttempts(2);
        entity.setLastFailureReason(null);
        return entity;
    }

    private ReminderJpaEntity reminderEntity(String status) {
        Instant createdAt = Instant.parse("2026-05-12T09:00:00Z");
        Instant processingStartedAt = Instant.parse("2026-05-12T10:01:00Z");
        ReminderJpaEntity entity = new ReminderJpaEntity();
        entity.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        entity.setTaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        entity.setRemindAt(Instant.parse("2026-05-12T10:00:00Z"));
        entity.setStatus(status);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(processingStartedAt);
        entity.setNextAttemptAt(Instant.parse("2026-05-12T10:00:00Z"));
        entity.setProcessingStartedAt(processingStartedAt);
        entity.setProcessingOwner("worker-1");
        entity.setDeliveredAt(null);
        entity.setDeliveryAttempts(1);
        entity.setLastFailureReason("previous timeout");
        return entity;
    }
}
