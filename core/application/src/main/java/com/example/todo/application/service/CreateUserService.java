package com.example.todo.application.service;

import com.example.todo.application.command.CreateUserCommand;
import com.example.todo.application.exception.AlreadyExistsException;
import com.example.todo.application.exception.ApplicationValidationException;
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

    public CreateUserService(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            Clock clock
    ) {
        this.loadUserPort = Objects.requireNonNull(loadUserPort, "loadUserPort must not be null");
        this.saveUserPort = Objects.requireNonNull(saveUserPort, "saveUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public User createUser(CreateUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String username = requireText(command.username(), "username");
        requireText(command.displayName(), "displayName");

        if (loadUserPort.existsByUsername(username)) {
            throw new AlreadyExistsException("username already exists: " + username);
        }

        User user = User.createNew(
                username,
                command.displayName(),
                command.telegramChatId(),
                clock.instant()
        );

        return saveUserPort.save(user);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApplicationValidationException(fieldName + " must not be blank");
        }
        return value;
    }
}