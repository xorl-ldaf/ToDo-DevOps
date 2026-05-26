package com.example.todo.application.outbox;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.outbox.OutboxPublicationPolicy.PublicationFailureDecision;
import com.example.todo.application.outbox.OutboxPublicationPolicy.PublicationFailureOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxPublicationPolicyTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Test
    void decideFailureShouldRetryWhenAttemptsRemain() {
        OutboxPublicationPolicy policy = new OutboxPublicationPolicy(5, Duration.ofSeconds(10));
        ReminderScheduledEventOutboxMessage message = outboxMessage(1);

        PublicationFailureDecision decision = policy.decideFailure(
                message,
                NOW,
                new PublicationException("broker unavailable")
        );

        assertEquals(PublicationFailureOutcome.RETRY, decision.outcome());
        assertEquals(NOW.plusSeconds(10), decision.nextAttemptAt());
        assertEquals("PublicationException", decision.failureReason());
    }

    @Test
    void decideFailureShouldMarkFailedWhenRetryBudgetIsExhausted() {
        OutboxPublicationPolicy policy = new OutboxPublicationPolicy(5, Duration.ofSeconds(10));
        ReminderScheduledEventOutboxMessage message = outboxMessage(4);

        PublicationFailureDecision decision = policy.decideFailure(
                message,
                NOW,
                new PublicationException("broker unavailable")
        );

        assertEquals(PublicationFailureOutcome.FAILED, decision.outcome());
        assertNull(decision.nextAttemptAt());
        assertEquals("PublicationException", decision.failureReason());
    }

    @Test
    void normalizeFailureReasonShouldUseExceptionSimpleClassName() {
        OutboxPublicationPolicy policy = new OutboxPublicationPolicy(5, Duration.ofSeconds(10));

        assertEquals(
                "PublicationException",
                policy.normalizeFailureReason(new PublicationException("broker unavailable"))
        );
    }

    @Test
    void constructorShouldRejectInvalidMaxAttempts() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxPublicationPolicy(0, Duration.ofSeconds(10))
        );

        assertEquals("maxDeliveryAttempts must be at least 1", exception.getMessage());
    }

    @Test
    void constructorShouldRejectInvalidRetryBackoff() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxPublicationPolicy(5, Duration.ZERO)
        );

        assertEquals("retryBackoff must be positive", exception.getMessage());
    }

    private ReminderScheduledEventOutboxMessage outboxMessage(int deliveryAttempts) {
        ReminderScheduledEventV1 event = new ReminderScheduledEventV1(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                NOW,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                NOW.plusSeconds(300),
                "SCHEDULED"
        );
        return new ReminderScheduledEventOutboxMessage(event.eventId(), event, deliveryAttempts, NOW);
    }

    private static final class PublicationException extends RuntimeException {
        private PublicationException(String message) {
            super(message);
        }
    }
}
