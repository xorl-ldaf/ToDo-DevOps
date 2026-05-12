package com.example.todo.application.port.out;

import com.example.todo.domain.user.User;

public interface SaveUserPort {
    User save(User user);
}