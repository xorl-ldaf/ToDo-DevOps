package com.example.todo.adapter.out.persistence.mapper;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.task.TaskId;

public final class ReminderPersistenceMapper {

    private ReminderPersistenceMapper() {
    }

    public static ReminderJpaEntity toJpa(Reminder reminder) {
        ReminderJpaEntity entity = new ReminderJpaEntity();
        entity.setId(reminder.getId().value());
        entity.setTaskId(reminder.getTaskId().value());
        entity.setRemindAt(reminder.getRemindAt());
        entity.setStatus(reminder.getStatus());
        entity.setCreatedAt(reminder.getCreatedAt());
        entity.setUpdatedAt(reminder.getUpdatedAt());
        entity.setSentAt(reminder.getSentAt());
        return entity;
    }

    public static Reminder toDomain(ReminderJpaEntity entity) {
        return Reminder.restore(
                new ReminderId(entity.getId()),
                new TaskId(entity.getTaskId()),
                entity.getRemindAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSentAt()
        );
    }
}
