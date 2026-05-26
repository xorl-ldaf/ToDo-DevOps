package com.example.todo.config;

import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.domain.task.Task;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalAssignTaskUseCase implements AssignTaskUseCase {
    private final AssignTaskUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalAssignTaskUseCase(AssignTaskUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
    }

    @Override
    public Task assignTask(AssignTaskCommand command) {
        return transactionTemplate.execute(status -> delegate.assignTask(command));
    }
}
