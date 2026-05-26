package com.example.todo.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ValidationError> validationErrors
) {
    public ApiError {
        validationErrors = List.copyOf(validationErrors);
    }

    public record ValidationError(String field, String message) {
    }
}
