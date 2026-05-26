package com.example.todo.application.port.in;

import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.user.User;

public interface ListUsersUseCase {
    PageResult<User> listUsers(PageQuery pageQuery);
}
