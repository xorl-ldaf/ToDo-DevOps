package com.example.todo.config;

import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

public final class TransactionalScanDueRemindersUseCase implements ScanDueRemindersUseCase {
    private final ScanDueRemindersUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalScanDueRemindersUseCase(
            ScanDueRemindersUseCase delegate,
            PlatformTransactionManager transactionManager
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public int scanAndPublishDueReminders(Instant now) {
        Integer deliveredCount = transactionTemplate.execute(status -> delegate.scanAndPublishDueReminders(now));
        return deliveredCount == null ? 0 : deliveredCount;
    }
}
