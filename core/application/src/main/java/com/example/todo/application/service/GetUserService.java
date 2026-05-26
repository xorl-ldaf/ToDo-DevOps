package com.example.todo.application.service;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

import java.util.Objects;
import java.util.Optional;

public class GetUserService implements GetUserUseCase {
    private final LoadUserDetailsPort loadUserDetailsPort;

    public GetUserService(LoadUserDetailsPort loadUserDetailsPort) {
        this.loadUserDetailsPort = Objects.requireNonNull(loadUserDetailsPort);
    }

    @Override
    public Optional<User> getUser(UserId userId) {
        if (userId == null) {
            throw new ApplicationValidationException("userId must not be null");
        }

        return loadUserDetailsPort.loadById(userId);
    }

    @Override
    public User getRequiredUser(UserId userId) {
        if (userId == null) {
            throw new ApplicationValidationException("userId must not be null");
        }

        return loadUserDetailsPort.loadById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + userId.value()));
    }
}
