package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query(
            value = """
                    select *
                    from reminder_scheduled_event_outbox
                    where event_id = :eventId
                      and status = :status
                      and processing_owner = :processingOwner
                    for update
                    """,
            nativeQuery = true
    )
    Optional<ReminderScheduledEventOutboxJpaEntity> findForUpdateByEventIdAndStatusAndProcessingOwner(
            @Param("eventId") UUID eventId,
            @Param("status") String status,
            @Param("processingOwner") String processingOwner
    );
}
