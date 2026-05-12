package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.adapter.ReminderScheduledEventOutboxPersistenceAdapter;
import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxJpaEntity;
import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxStatus;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderScheduledEventOutboxRepositoryIT extends AbstractReminderPersistenceRepositoryIT {

    private ReminderScheduledEventOutboxPersistenceAdapter outboxAdapter;

    @BeforeEach
    void setUpOutboxAdapter() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        outboxAdapter = new ReminderScheduledEventOutboxPersistenceAdapter(outboxRepository, objectMapper);
    }

    @Test
    void claimPendingShouldClaimOnlyAvailablePendingMessages() {
        UUID dueEventId = storeOutboxEvent(NOW.minusSeconds(1));
        UUID futureEventId = storeOutboxEvent(NOW.plusSeconds(60));

        List<ReminderScheduledEventOutboxMessage> claimed =
                outboxAdapter.claimPending(NOW, "outbox-worker-1", Duration.ofSeconds(30), 10);

        assertThat(claimed).extracting(ReminderScheduledEventOutboxMessage::eventId).containsExactly(dueEventId);
        assertThat(requireOutboxMessage(dueEventId)).satisfies(entity -> {
            assertThat(entity.getStatus()).isEqualTo(ReminderScheduledEventOutboxStatus.PROCESSING);
            assertThat(entity.getProcessingOwner()).isEqualTo("outbox-worker-1");
            assertThat(entity.getProcessingStartedAt()).isEqualTo(NOW);
            assertThat(entity.getDeliveryAttempts()).isZero();
            assertThat(entity.getLastFailureReason()).isNull();
        });
        assertThat(requireOutboxMessage(futureEventId).getStatus()).isEqualTo(ReminderScheduledEventOutboxStatus.PENDING);
    }

    @Test
    void markPublishedShouldSucceedOnlyWithMatchingProcessingOwner() {
        UUID eventId = claimStoredEvent("outbox-worker-1");

        boolean wrongOwnerResult = outboxAdapter.markPublished(eventId, "outbox-worker-2", NOW.plusSeconds(1));
        boolean matchingOwnerResult = outboxAdapter.markPublished(eventId, "outbox-worker-1", NOW.plusSeconds(1));

        assertThat(wrongOwnerResult).isFalse();
        assertThat(matchingOwnerResult).isTrue();
        assertThat(requireOutboxMessage(eventId)).satisfies(entity -> {
            assertThat(entity.getStatus()).isEqualTo(ReminderScheduledEventOutboxStatus.PUBLISHED);
            assertThat(entity.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
            assertThat(entity.getProcessingOwner()).isNull();
            assertThat(entity.getProcessingStartedAt()).isNull();
            assertThat(entity.getDeliveryAttempts()).isEqualTo(1);
            assertThat(entity.getLastFailureReason()).isNull();
        });
    }

    @Test
    void rescheduleShouldReturnMessageToPendingWithNextAttemptAndFailureReason() {
        UUID eventId = claimStoredEvent("outbox-worker-1");
        Instant nextAttemptAt = NOW.plusSeconds(60);

        boolean result = outboxAdapter.reschedule(
                eventId,
                "outbox-worker-1",
                NOW.plusSeconds(1),
                nextAttemptAt,
                "KafkaException"
        );

        assertThat(result).isTrue();
        assertThat(requireOutboxMessage(eventId)).satisfies(entity -> {
            assertThat(entity.getStatus()).isEqualTo(ReminderScheduledEventOutboxStatus.PENDING);
            assertThat(entity.getAvailableAt()).isEqualTo(nextAttemptAt);
            assertThat(entity.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
            assertThat(entity.getProcessingOwner()).isNull();
            assertThat(entity.getProcessingStartedAt()).isNull();
            assertThat(entity.getDeliveryAttempts()).isEqualTo(1);
            assertThat(entity.getLastFailureReason()).isEqualTo("KafkaException");
        });
    }

    @Test
    void markFailedShouldMoveClaimedMessageToFailedWithFailureReason() {
        UUID eventId = claimStoredEvent("outbox-worker-1");

        boolean result = outboxAdapter.markFailed(
                eventId,
                "outbox-worker-1",
                NOW.plusSeconds(1),
                "KafkaException"
        );

        assertThat(result).isTrue();
        assertThat(requireOutboxMessage(eventId)).satisfies(entity -> {
            assertThat(entity.getStatus()).isEqualTo(ReminderScheduledEventOutboxStatus.FAILED);
            assertThat(entity.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
            assertThat(entity.getProcessingOwner()).isNull();
            assertThat(entity.getProcessingStartedAt()).isNull();
            assertThat(entity.getDeliveryAttempts()).isEqualTo(1);
            assertThat(entity.getLastFailureReason()).isEqualTo("KafkaException");
        });
    }

    private UUID claimStoredEvent(String owner) {
        UUID eventId = storeOutboxEvent(NOW);

        List<ReminderScheduledEventOutboxMessage> claimed =
                outboxAdapter.claimPending(NOW, owner, Duration.ofSeconds(30), 10);

        assertThat(claimed).extracting(ReminderScheduledEventOutboxMessage::eventId).containsExactly(eventId);
        return eventId;
    }

    private UUID storeOutboxEvent(Instant occurredAt) {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(scheduledReminder(reminderId, taskId, NOW));

        ReminderScheduledEventV1 event = new ReminderScheduledEventV1(
                UUID.randomUUID(),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                occurredAt,
                reminderId,
                taskId,
                NOW,
                "SCHEDULED"
        );
        outboxAdapter.store(event);
        return event.eventId();
    }

    private ReminderScheduledEventOutboxJpaEntity requireOutboxMessage(UUID eventId) {
        return outboxRepository.findById(eventId).orElseThrow();
    }
}
