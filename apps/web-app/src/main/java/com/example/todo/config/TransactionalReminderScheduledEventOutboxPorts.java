package com.example.todo.config;

import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
import com.example.todo.application.port.out.ClaimReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.FinalizeReminderScheduledEventOutboxPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime transaction boundary for outbox claim/finalize steps.
 * Kafka publication happens between these port calls and stays outside a DB transaction.
 */
public final class TransactionalReminderScheduledEventOutboxPorts implements
        ClaimReminderScheduledEventOutboxPort,
        FinalizeReminderScheduledEventOutboxPort {

    private final ClaimReminderScheduledEventOutboxPort claimOutboxPort;
    private final FinalizeReminderScheduledEventOutboxPort finalizeOutboxPort;
    private final TransactionTemplate transactionTemplate;

    public TransactionalReminderScheduledEventOutboxPorts(
            ClaimReminderScheduledEventOutboxPort claimOutboxPort,
            FinalizeReminderScheduledEventOutboxPort finalizeOutboxPort,
            PlatformTransactionManager transactionManager
    ) {
        this.claimOutboxPort = Objects.requireNonNull(claimOutboxPort, "claimOutboxPort must not be null");
        this.finalizeOutboxPort = Objects.requireNonNull(finalizeOutboxPort, "finalizeOutboxPort must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<ReminderScheduledEventOutboxMessage> claimPending(
            Instant now,
            String processorId,
            Duration processingTimeout,
            int limit
    ) {
        return transactionTemplate.execute(status -> claimOutboxPort.claimPending(
                now,
                processorId,
                processingTimeout,
                limit
        ));
    }

    @Override
    public boolean markPublished(UUID eventId, String processorId, Instant publishedAt) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> finalizeOutboxPort.markPublished(eventId, processorId, publishedAt)
        ));
    }

    @Override
    public boolean reschedule(
            UUID eventId,
            String processorId,
            Instant processedAt,
            Instant nextAttemptAt,
            String failureReason
    ) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> finalizeOutboxPort.reschedule(
                        eventId,
                        processorId,
                        processedAt,
                        nextAttemptAt,
                        failureReason
                )
        ));
    }

    @Override
    public boolean markFailed(UUID eventId, String processorId, Instant processedAt, String failureReason) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> finalizeOutboxPort.markFailed(eventId, processorId, processedAt, failureReason)
        ));
    }
}
