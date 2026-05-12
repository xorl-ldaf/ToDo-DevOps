package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventReceiptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataReminderScheduledEventReceiptRepository
        extends JpaRepository<ReminderScheduledEventReceiptJpaEntity, UUID> {
}
