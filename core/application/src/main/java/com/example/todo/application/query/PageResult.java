package com.example.todo.application.query;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PageResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than 0");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be greater than or equal to 0");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be greater than or equal to 0");
        }
    }
}
