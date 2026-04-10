package com.example.todo.application.port.out;

import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

import java.util.Optional;

public interface LoadUserDetailsPort {
    Optional<User> loadById(UserId userId);
}