package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataReminderRepository extends JpaRepository<ReminderJpaEntity, UUID> {
    List<ReminderJpaEntity> findByStatusAndRemindAtLessThanEqual(String status, Instant remindAt);

    List<ReminderJpaEntity> findByTaskIdOrderByRemindAtAsc(UUID taskId);
}