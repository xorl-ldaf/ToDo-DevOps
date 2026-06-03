package com.example.todo.application.service;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.application.port.out.LoadAllUsersPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.user.User;

import java.util.Objects;

public class ListUsersService implements ListUsersUseCase {
    private final LoadAllUsersPort loadAllUsersPort;

    public ListUsersService(LoadAllUsersPort loadAllUsersPort) {
        this.loadAllUsersPort = Objects.requireNonNull(loadAllUsersPort);
    }

    @Override
    public PageResult<User> listUsers(PageQuery pageQuery) {
        return loadAllUsersPort.load(requireNonNull(pageQuery, "pageQuery"));
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
