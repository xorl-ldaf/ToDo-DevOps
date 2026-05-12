package com.example.todo.application.port.out;

import com.example.todo.domain.user.UserId;

public interface LoadUserPort {
    boolean existsById(UserId userId);

    boolean existsByUsername(String username);
}