package com.example.todo.application.port.in;

import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

import java.util.Optional;

public interface GetUserUseCase {
    Optional<User> getUser(UserId userId);
}