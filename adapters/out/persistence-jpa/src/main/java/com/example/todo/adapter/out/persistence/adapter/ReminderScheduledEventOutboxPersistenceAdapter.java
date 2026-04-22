package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxJpaEntity;
import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxStatus;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderScheduledEventOutboxRepository;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
import com.example.todo.application.port.out.ClaimReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.FinalizeReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.StoreReminderScheduledEventPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class ReminderScheduledEventOutboxPersistenceAdapter implements
        StoreReminderScheduledEventPort,
        ClaimReminderScheduledEventOutboxPort,
        FinalizeReminderScheduledEventOutboxPort {

    private final SpringDataReminderScheduledEventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public ReminderScheduledEventOutboxPersistenceAdapter(
            SpringDataReminderScheduledEventOutboxRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(ReminderScheduledEventV1 event) {
        ReminderScheduledEventV1 actualEvent = Objects.requireNonNull(event, "event must not be null");
        ReminderScheduledEventOutboxJpaEntity entity = new ReminderScheduledEventOutboxJpaEntity();
        entity.setEventId(actualEvent.eventId());
        entity.setReminderId(actualEvent.reminderId());
        entity.setTaskId(actualEvent.taskId());
        entity.setPayload(writePayload(actualEvent));
        entity.setStatus(ReminderScheduledEventOutboxStatus.PENDING);
        entity.setCreatedAt(actualEvent.occurredAt());
        entity.setUpdatedAt(actualEvent.occurredAt());
        entity.setAvailableAt(actualEvent.occurredAt());
        entity.setDeliveryAttempts(0);
        repository.save(entity);
    }

    @Override
    @Transactional
    public List<ReminderScheduledEventOutboxMessage> claimPending(
            Instant now,
            String processorId,
            Duration processingTimeout,
            int limit
    ) {
        List<ReminderScheduledEventOutboxMessage> claimedMessages = new ArrayList<>();
        for (ReminderScheduledEventOutboxJpaEntity entity : repository.findClaimableForPublishing(
                now,
                now.minus(processingTimeout),
                limit
        )) {
            entity.setStatus(ReminderScheduledEventOutboxStatus.PROCESSING);
            entity.setProcessingOwner(processorId);
            entity.setProcessingStartedAt(now);
            entity.setUpdatedAt(now);
            entity.setLastFailureReason(null);
            repository.save(entity);
            claimedMessages.add(toMessage(entity));
        }
        return claimedMessages;
    }

    @Override
    @Transactional
    public boolean markPublished(UUID eventId, String processorId, Instant publishedAt) {
        return withClaimedMessage(eventId, processorId, entity -> {
            entity.setStatus(ReminderScheduledEventOutboxStatus.PUBLISHED);
            entity.setPublishedAt(publishedAt);
            entity.setUpdatedAt(publishedAt);
            entity.setProcessingOwner(null);
            entity.setProcessingStartedAt(null);
            entity.setLastFailureReason(null);
            entity.setDeliveryAttempts(entity.getDeliveryAttempts() + 1);
        });
    }

    @Override
    @Transactional
    public boolean reschedule(
            UUID eventId,
            String processorId,
            Instant processedAt,
            Instant nextAttemptAt,
            String failureReason
    ) {
        return withClaimedMessage(eventId, processorId, entity -> {
            entity.setStatus(ReminderScheduledEventOutboxStatus.PENDING);
            entity.setAvailableAt(nextAttemptAt);
            entity.setUpdatedAt(processedAt);
            entity.setProcessingOwner(null);
            entity.setProcessingStartedAt(null);
            entity.setLastFailureReason(failureReason);
            entity.setDeliveryAttempts(entity.getDeliveryAttempts() + 1);
        });
    }

    @Override
    @Transactional
    public boolean markFailed(UUID eventId, String processorId, Instant processedAt, String failureReason) {
        return withClaimedMessage(eventId, processorId, entity -> {
            entity.setStatus(ReminderScheduledEventOutboxStatus.FAILED);
            entity.setUpdatedAt(processedAt);
            entity.setProcessingOwner(null);
            entity.setProcessingStartedAt(null);
            entity.setLastFailureReason(failureReason);
            entity.setDeliveryAttempts(entity.getDeliveryAttempts() + 1);
        });
    }

    private boolean withClaimedMessage(UUID eventId, String processorId, Consumer<ReminderScheduledEventOutboxJpaEntity> action) {
        return repository.findForUpdateByEventIdAndStatusAndProcessingOwner(
                        eventId,
                        "PROCESSING",
                        processorId
                )
                .map(entity -> {
                    action.accept(entity);
                    repository.save(entity);
                    return true;
                })
                .orElse(false);
    }

    private ReminderScheduledEventOutboxMessage toMessage(ReminderScheduledEventOutboxJpaEntity entity) {
        return new ReminderScheduledEventOutboxMessage(
                entity.getEventId(),
                readPayload(entity.getPayload()),
                entity.getDeliveryAttempts(),
                entity.getAvailableAt()
        );
    }

    private String writePayload(ReminderScheduledEventV1 event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reminder scheduled event outbox payload", exception);
        }
    }

    private ReminderScheduledEventV1 readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ReminderScheduledEventV1.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize reminder scheduled event outbox payload", exception);
        }
    }
}
