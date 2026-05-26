package com.example.todo.application.service;

import com.example.todo.application.command.CreateUserCommand;
import com.example.todo.application.exception.AlreadyExistsException;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.factory.UserFactory;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveUserPort;
import com.example.todo.domain.user.User;

import java.time.Clock;
import java.util.Objects;

public class CreateUserService implements CreateUserUseCase {
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final Clock clock;
    private final UserFactory userFactory;

    public CreateUserService(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            Clock clock
    ) {
        this(loadUserPort, saveUserPort, clock, new UserFactory());
    }

    public CreateUserService(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            Clock clock,
            UserFactory userFactory
    ) {
        this.loadUserPort = Objects.requireNonNull(loadUserPort, "loadUserPort must not be null");
        this.saveUserPort = Objects.requireNonNull(saveUserPort, "saveUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.userFactory = Objects.requireNonNull(userFactory, "userFactory must not be null");
    }

    @Override
    public User createUser(CreateUserCommand command) {
        if (command == null) {
            throw new ApplicationValidationException("command must not be null");
        }

        User user = userFactory.create(
                command.username(),
                command.displayName(),
                command.telegramChatId(),
                clock.instant()
        );

        if (loadUserPort.existsByUsername(user.getUsername())) {
            throw new AlreadyExistsException("username already exists: " + user.getUsername());
        }

        return saveUserPort.save(user);
    }
}
