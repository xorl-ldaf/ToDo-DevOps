package com.example.todo.config;

import com.example.todo.application.command.CreateUserCommand;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.domain.user.User;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalCreateUserUseCase implements CreateUserUseCase {
    private final CreateUserUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalCreateUserUseCase(CreateUserUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
    }

    @Override
    public User createUser(CreateUserCommand command) {
        return transactionTemplate.execute(status -> delegate.createUser(command));
    }
}
