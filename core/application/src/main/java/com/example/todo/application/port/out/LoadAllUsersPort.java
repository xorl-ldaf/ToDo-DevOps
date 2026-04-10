package com.example.todo.application.port.out;

import com.example.todo.domain.user.User;

import java.util.List;

public interface LoadAllUsersPort {
    List<User> loadAll();
}