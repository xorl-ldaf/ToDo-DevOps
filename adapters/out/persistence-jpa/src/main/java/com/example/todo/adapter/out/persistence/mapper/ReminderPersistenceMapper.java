package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;

public final class ReminderPersistenceMapper {

    private ReminderPersistenceMapper() {
    }

    public static ReminderJpaEntity toJpa(Reminder reminder) {
        ReminderJpaEntity entity = new ReminderJpaEntity();
        entity.setId(reminder.getId().value());
        entity.setTaskId(reminder.getTaskId().value());
        entity.setRemindAt(reminder.getRemindAt());
        entity.setStatus(toPersistenceStatus(reminder.getStatus()));
        entity.setCreatedAt(reminder.getCreatedAt());
        entity.setUpdatedAt(reminder.getUpdatedAt());
        entity.setNextAttemptAt(reminder.getNextAttemptAt());
        entity.setProcessingStartedAt(reminder.getProcessingStartedAt());
        entity.setProcessingOwner(reminder.getProcessingOwner());
        entity.setDeliveredAt(reminder.getDeliveredAt());
        entity.setDeliveryAttempts(reminder.getDeliveryAttempts());
        entity.setLastFailureReason(reminder.getLastFailureReason());
        return entity;
    }

    public static Reminder toDomain(ReminderJpaEntity entity) {
        return Reminder.restore(
                new ReminderId(entity.getId()),
                new TaskId(entity.getTaskId()),
                entity.getRemindAt(),
                toDomainStatus(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getNextAttemptAt(),
                entity.getProcessingStartedAt(),
                entity.getProcessingOwner(),
                entity.getDeliveredAt(),
                entity.getDeliveryAttempts(),
                entity.getLastFailureReason()
        );
    }

    private static String toPersistenceStatus(ReminderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Reminder status persistence value must not be null");
        }
        return status.name();
    }

    private static ReminderStatus toDomainStatus(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Reminder status persistence value must not be null");
        }
        try {
            return ReminderStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown reminder status persistence value: " + value, exception);
        }
    }
}
