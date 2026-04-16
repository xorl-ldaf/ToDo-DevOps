package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.dto.CreateUserRequest;
import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.adapter.in.web.mapper.WebApiMapper;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.domain.user.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final ListUsersUseCase listUsersUseCase;

    public UserController(
            CreateUserUseCase createUserUseCase,
            GetUserUseCase getUserUseCase,
            ListUsersUseCase listUsersUseCase
    ) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
        this.listUsersUseCase = listUsersUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return WebApiMapper.toResponse(
                createUserUseCase.createUser(WebApiMapper.toCommand(request))
        );
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
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + userId));
    }
}