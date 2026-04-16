package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataReminderRepository extends JpaRepository<ReminderJpaEntity, UUID> {
    List<ReminderJpaEntity> findByStatusAndRemindAtLessThanEqual(ReminderStatus status, Instant remindAt);

    List<ReminderJpaEntity> findByTaskIdOrderByRemindAtAsc(UUID taskId);
}