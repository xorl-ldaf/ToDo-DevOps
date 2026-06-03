package com.example.todo.application.service;

import com.example.todo.application.outbox.OutboxPublicationPolicy;
import com.example.todo.application.outbox.OutboxPublicationPolicy.PublicationFailureDecision;
import com.example.todo.application.outbox.OutboxPublicationPolicy.PublicationFailureOutcome;
import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.in.FlushReminderScheduledEventOutboxUseCase;
import com.example.todo.application.port.in.ReminderScheduledEventOutboxReport;
import com.example.todo.application.port.out.ClaimReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.FinalizeReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class FlushReminderScheduledEventOutboxService implements FlushReminderScheduledEventOutboxUseCase {
    private final ClaimReminderScheduledEventOutboxPort claimReminderScheduledEventOutboxPort;
    private final FinalizeReminderScheduledEventOutboxPort finalizeReminderScheduledEventOutboxPort;
    private final PublishReminderScheduledEventPort publishReminderScheduledEventPort;
    private final String processorId;
    private final int batchSize;
    private final Duration processingTimeout;
    private final OutboxPublicationPolicy outboxPublicationPolicy;

    public FlushReminderScheduledEventOutboxService(
            ClaimReminderScheduledEventOutboxPort claimReminderScheduledEventOutboxPort,
            FinalizeReminderScheduledEventOutboxPort finalizeReminderScheduledEventOutboxPort,
            PublishReminderScheduledEventPort publishReminderScheduledEventPort,
            String processorId,
            int batchSize,
            int maxDeliveryAttempts,
            Duration retryBackoff,
            Duration processingTimeout
    ) {
        this(
                claimReminderScheduledEventOutboxPort,
                finalizeReminderScheduledEventOutboxPort,
                publishReminderScheduledEventPort,
                processorId,
                batchSize,
                processingTimeout,
                new OutboxPublicationPolicy(maxDeliveryAttempts, retryBackoff)
        );
    }

    FlushReminderScheduledEventOutboxService(
            ClaimReminderScheduledEventOutboxPort claimReminderScheduledEventOutboxPort,
            FinalizeReminderScheduledEventOutboxPort finalizeReminderScheduledEventOutboxPort,
            PublishReminderScheduledEventPort publishReminderScheduledEventPort,
            String processorId,
            int batchSize,
            Duration processingTimeout,
            OutboxPublicationPolicy outboxPublicationPolicy
    ) {
        this.claimReminderScheduledEventOutboxPort = Objects.requireNonNull(
                claimReminderScheduledEventOutboxPort,
                "claimReminderScheduledEventOutboxPort must not be null"
        );
        this.finalizeReminderScheduledEventOutboxPort = Objects.requireNonNull(
                finalizeReminderScheduledEventOutboxPort,
                "finalizeReminderScheduledEventOutboxPort must not be null"
        );
        this.publishReminderScheduledEventPort = Objects.requireNonNull(
                publishReminderScheduledEventPort,
                "publishReminderScheduledEventPort must not be null"
        );
        this.processorId = requireText(processorId, "processorId");
        this.batchSize = requirePositive(batchSize, "batchSize");
        this.processingTimeout = requirePositive(processingTimeout, "processingTimeout");
        this.outboxPublicationPolicy = Objects.requireNonNull(
                outboxPublicationPolicy,
                "outboxPublicationPolicy must not be null"
        );
    }

    @Override
    public ReminderScheduledEventOutboxReport flush(Instant now) {
        Instant actualNow = requireNonNull(now, "now");

        List<ReminderScheduledEventOutboxMessage> messages = claimReminderScheduledEventOutboxPort.claimPending(
                actualNow,
                processorId,
                processingTimeout,
                batchSize
        );
        if (messages.isEmpty()) {
            return ReminderScheduledEventOutboxReport.empty();
        }

        int publishedCount = 0;
        int retriedCount = 0;
        int failedCount = 0;
        int concurrencyConflictCount = 0;

        for (ReminderScheduledEventOutboxMessage message : messages) {
            try {
                publishReminderScheduledEventPort.publish(message.event());
                if (!finalizeReminderScheduledEventOutboxPort.markPublished(message.eventId(), processorId, actualNow)) {
                    concurrencyConflictCount++;
                } else {
                    publishedCount++;
                }
            } catch (RuntimeException exception) {
                PublicationFailureDecision decision = outboxPublicationPolicy.decideFailure(message, actualNow, exception);
                if (decision.outcome() == PublicationFailureOutcome.RETRY) {
                    if (!finalizeReminderScheduledEventOutboxPort.reschedule(
                            message.eventId(),
                            processorId,
                            actualNow,
                            decision.nextAttemptAt(),
                            decision.failureReason()
                    )) {
                        concurrencyConflictCount++;
                    } else {
                        retriedCount++;
                    }
                    continue;
                }

                if (!finalizeReminderScheduledEventOutboxPort.markFailed(
                        message.eventId(),
                        processorId,
                        actualNow,
                        decision.failureReason()
                )) {
                    concurrencyConflictCount++;
                } else {
                    failedCount++;
                }
            }
        }

        return new ReminderScheduledEventOutboxReport(
                messages.size(),
                publishedCount,
                retriedCount,
                failedCount,
                concurrencyConflictCount
        );
    }

    private static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new ApplicationValidationException(fieldName + " must be at least 1");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Duration actualValue = requireNonNull(value, fieldName);
        if (actualValue.isNegative() || actualValue.isZero()) {
            throw new ApplicationValidationException(fieldName + " must be positive");
        }
        return actualValue;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = requireNonNull(value, fieldName);
        if (actualValue.isBlank()) {
            throw new ApplicationValidationException(fieldName + " must not be blank");
        }
        return actualValue;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
