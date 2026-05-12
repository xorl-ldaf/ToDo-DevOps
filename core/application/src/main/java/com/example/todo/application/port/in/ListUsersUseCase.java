package com.example.todo.application.port.in;

import com.example.todo.domain.user.User;

import java.util.List;

public interface ListUsersUseCase {
    List<User> listUsers();
}