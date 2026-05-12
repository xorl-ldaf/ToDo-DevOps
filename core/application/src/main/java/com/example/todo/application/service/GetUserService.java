package com.example.todo.application.service;

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
        return loadUserDetailsPort.loadById(userId);
    }
}