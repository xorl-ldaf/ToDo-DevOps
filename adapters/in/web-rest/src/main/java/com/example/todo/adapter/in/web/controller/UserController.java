package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.dto.CreateUserRequest;
import com.example.todo.adapter.in.web.dto.PageResponse;
import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.adapter.in.web.mapper.WebApiMapper;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.application.query.PageQuery;
import com.example.todo.domain.user.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public PageResponse<UserResponse> listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", required = false) String sort
    ) {
        return WebApiMapper.toResponse(
                listUsersUseCase.listUsers(new PageQuery(page, size, sort)),
                WebApiMapper::toResponse
        );
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable("userId") UUID userId) {
        return WebApiMapper.toResponse(getUserUseCase.getRequiredUser(new UserId(userId)));
    }
}
