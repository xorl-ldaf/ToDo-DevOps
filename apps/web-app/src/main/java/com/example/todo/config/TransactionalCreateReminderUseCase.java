package com.example.todo.config;

import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.domain.reminder.Reminder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalCreateReminderUseCase implements CreateReminderUseCase {
    private final CreateReminderUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalCreateReminderUseCase(
            CreateReminderUseCase delegate,
            PlatformTransactionManager transactionManager
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Reminder createReminder(CreateReminderCommand command) {
        return transactionTemplate.execute(status -> delegate.createReminder(command));
    }
}
