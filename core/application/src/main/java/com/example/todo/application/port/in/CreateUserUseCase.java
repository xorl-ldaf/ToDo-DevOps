package com.example.todo.application.port.in;

import com.example.todo.application.command.CreateUserCommand;
import com.example.todo.domain.user.User;

public interface CreateUserUseCase {
    User createUser(CreateUserCommand command);
}