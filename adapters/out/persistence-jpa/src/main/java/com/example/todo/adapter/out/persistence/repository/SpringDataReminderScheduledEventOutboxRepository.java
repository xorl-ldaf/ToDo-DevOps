package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxJpaEntity;
import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataReminderScheduledEventOutboxRepository
        extends JpaRepository<ReminderScheduledEventOutboxJpaEntity, UUID> {

    @Query(
            value = """
                    select *
                    from reminder_scheduled_event_outbox
                    where (
                            status = 'PENDING'
                            and available_at <= :now
                        ) or (
                            status = 'PROCESSING'
                            and processing_started_at <= :staleBefore
                        )
                    order by available_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<ReminderScheduledEventOutboxJpaEntity> findClaimableForPublishing(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("limit") int limit
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select message
            from ReminderScheduledEventOutboxJpaEntity message
            where message.eventId = :eventId
              and message.status = :status
              and message.processingOwner = :processingOwner
            """)
    Optional<ReminderScheduledEventOutboxJpaEntity> findForUpdateByEventIdAndStatusAndProcessingOwner(
            @Param("eventId") UUID eventId,
            @Param("status") ReminderScheduledEventOutboxStatus status,
            @Param("processingOwner") String processingOwner
    );
}
