package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataReminderRepository extends JpaRepository<ReminderJpaEntity, UUID> {
    @Query(
            value = """
                    select *
                    from reminders
                    where status = 'PENDING'
                      and remind_at <= :remindAt
                    order by remind_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<ReminderJpaEntity> findDuePendingForDelivery(
            @Param("remindAt") Instant remindAt,
            @Param("limit") int limit
    );

    List<ReminderJpaEntity> findByTaskIdOrderByRemindAtAsc(UUID taskId);
}
