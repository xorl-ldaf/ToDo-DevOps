package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.ReminderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataReminderRepository extends JpaRepository<ReminderJpaEntity, UUID> {
    @Query(
            value = """
                    select *
                    from reminders
                    where (
                            status = 'SCHEDULED'
                            and next_attempt_at <= :now
                        ) or (
                            status = 'PROCESSING'
                            and processing_started_at <= :staleBefore
                        )
                    order by next_attempt_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<ReminderJpaEntity> findClaimableForProcessing(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("limit") int limit
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reminder
            from ReminderJpaEntity reminder
            where reminder.id = :id
              and reminder.status = :status
              and reminder.processingOwner = :processingOwner
            """)
    Optional<ReminderJpaEntity> findForUpdateByIdAndStatusAndProcessingOwner(
            @Param("id") UUID id,
            @Param("status") ReminderStatus status,
            @Param("processingOwner") String processingOwner
    );

    List<ReminderJpaEntity> findByTaskIdOrderByRemindAtAsc(UUID taskId);
}
