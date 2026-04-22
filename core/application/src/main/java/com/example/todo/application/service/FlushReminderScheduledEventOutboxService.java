package com.example.todo.application.service;

import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
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
    private final int maxDeliveryAttempts;
    private final Duration retryBackoff;
    private final Duration processingTimeout;

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
        this.maxDeliveryAttempts = requirePositive(maxDeliveryAttempts, "maxDeliveryAttempts");
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        this.processingTimeout = requirePositive(processingTimeout, "processingTimeout");
    }

    @Override
    public ReminderScheduledEventOutboxReport flush(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        List<ReminderScheduledEventOutboxMessage> messages = claimReminderScheduledEventOutboxPort.claimPending(
                now,
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
                if (!finalizeReminderScheduledEventOutboxPort.markPublished(message.eventId(), processorId, now)) {
                    concurrencyConflictCount++;
                } else {
                    publishedCount++;
                }
            } catch (RuntimeException exception) {
                String failureReason = exception.getClass().getSimpleName();
                if (message.deliveryAttempts() + 1 < maxDeliveryAttempts) {
                    if (!finalizeReminderScheduledEventOutboxPort.reschedule(
                            message.eventId(),
                            processorId,
                            now,
                            now.plus(retryBackoff),
                            failureReason
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
                        now,
                        failureReason
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

    private static String requireText(String value, String fieldName) {
        String actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }
}
