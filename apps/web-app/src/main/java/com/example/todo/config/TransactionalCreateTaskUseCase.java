package com.example.todo.config;

import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.domain.task.Task;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalCreateTaskUseCase implements CreateTaskUseCase {
    private final CreateTaskUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalCreateTaskUseCase(CreateTaskUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
    }

    @Override
    public Task createTask(CreateTaskCommand command) {
        return transactionTemplate.execute(status -> delegate.createTask(command));
    }
}
