package com.example.todo.application.port.out;

import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.user.User;

public interface LoadAllUsersPort {
    PageResult<User> load(PageQuery pageQuery);
}
