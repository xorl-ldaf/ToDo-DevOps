package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.adapter.in.web.mapper.WebApiMapper;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.domain.user.UserId;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final ListUsersUseCase listUsersUseCase;

    public UserController(
            GetUserUseCase getUserUseCase,
            ListUsersUseCase listUsersUseCase
    ) {
        this.getUserUseCase = getUserUseCase;
        this.listUsersUseCase = listUsersUseCase;
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return listUsersUseCase.listUsers()
                .stream()
                .map(WebApiMapper::toResponse)
                .toList();
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        return getUserUseCase.getUser(new UserId(userId))
                .map(WebApiMapper::toResponse)
                .orElseThrow(() -> new NoSuchElementException("user not found: " + userId));
    }
}