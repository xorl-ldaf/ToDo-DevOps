package com.example.todo.config;

import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.domain.reminder.Reminder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Runtime transaction boundary for reminder worker persistence steps.
 * Delivery itself happens between these port calls and stays outside a DB transaction.
 */
public final class TransactionalReminderDeliveryPersistencePorts implements
        ClaimDueRemindersPort,
        FinalizeReminderDeliveryPort {

    private final ClaimDueRemindersPort claimDueRemindersPort;
    private final FinalizeReminderDeliveryPort finalizeReminderDeliveryPort;
    private final TransactionTemplate transactionTemplate;

    public TransactionalReminderDeliveryPersistencePorts(
            ClaimDueRemindersPort claimDueRemindersPort,
            FinalizeReminderDeliveryPort finalizeReminderDeliveryPort,
            PlatformTransactionManager transactionManager
    ) {
        this.claimDueRemindersPort = Objects.requireNonNull(
                claimDueRemindersPort,
                "claimDueRemindersPort must not be null"
        );
        this.finalizeReminderDeliveryPort = Objects.requireNonNull(
                finalizeReminderDeliveryPort,
                "finalizeReminderDeliveryPort must not be null"
        );
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public List<Reminder> claimDueReminders(
            Instant now,
            Duration processingTimeout,
            int limit,
            UnaryOperator<Reminder> claimTransition
    ) {
        return transactionTemplate.execute(status -> claimDueRemindersPort.claimDueReminders(
                now,
                processingTimeout,
                limit,
                claimTransition
        ));
    }

    @Override
    public boolean finalizeDelivery(Reminder reminder, String processorId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> finalizeReminderDeliveryPort.finalizeDelivery(reminder, processorId)
        ));
    }
}
