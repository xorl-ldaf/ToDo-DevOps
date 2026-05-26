package com.example.todo.application.query;

import com.example.todo.application.exception.ApplicationValidationException;

public record PageQuery(int page, int size, String sort) {
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new ApplicationValidationException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ApplicationValidationException("size must be between 1 and " + MAX_SIZE);
        }
        if (sort != null && sort.isBlank()) {
            sort = null;
        }
    }
}
