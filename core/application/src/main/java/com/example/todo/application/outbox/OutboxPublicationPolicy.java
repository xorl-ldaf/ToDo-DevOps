package com.example.todo.application.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class OutboxPublicationPolicy {
    private final int maxDeliveryAttempts;
    private final Duration retryBackoff;

    public OutboxPublicationPolicy(int maxDeliveryAttempts, Duration retryBackoff) {
        this.maxDeliveryAttempts = requirePositive(maxDeliveryAttempts, "maxDeliveryAttempts");
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
    }

    public PublicationFailureDecision decideFailure(
            ReminderScheduledEventOutboxMessage message,
            Instant now,
            RuntimeException exception
    ) {
        ReminderScheduledEventOutboxMessage actualMessage = Objects.requireNonNull(message, "message must not be null");
        Instant actualNow = Objects.requireNonNull(now, "now must not be null");
        String failureReason = normalizeFailureReason(exception);

        if (actualMessage.deliveryAttempts() + 1 < maxDeliveryAttempts) {
            return PublicationFailureDecision.retry(actualNow.plus(retryBackoff), failureReason);
        }
        return PublicationFailureDecision.failed(failureReason);
    }

    public String normalizeFailureReason(RuntimeException exception) {
        RuntimeException actualException = Objects.requireNonNull(exception, "exception must not be null");
        return actualException.getClass().getSimpleName();
    }

    private static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Duration actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isNegative() || actualValue.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return actualValue;
    }

    public enum PublicationFailureOutcome {
        RETRY,
        FAILED
    }

    public record PublicationFailureDecision(
            PublicationFailureOutcome outcome,
            Instant nextAttemptAt,
            String failureReason
    ) {
        public PublicationFailureDecision {
            outcome = Objects.requireNonNull(outcome, "outcome must not be null");
            failureReason = Objects.requireNonNull(failureReason, "failureReason must not be null");
            if (outcome == PublicationFailureOutcome.RETRY) {
                nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
            }
        }

        public static PublicationFailureDecision retry(Instant nextAttemptAt, String failureReason) {
            return new PublicationFailureDecision(PublicationFailureOutcome.RETRY, nextAttemptAt, failureReason);
        }

        public static PublicationFailureDecision failed(String failureReason) {
            return new PublicationFailureDecision(PublicationFailureOutcome.FAILED, null, failureReason);
        }
    }
}
