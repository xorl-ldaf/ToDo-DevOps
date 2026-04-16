package com.example.todo.adapter.in.web.advice;

import com.example.todo.adapter.in.web.dto.ApiError;
import com.example.todo.application.exception.AlreadyExistsException;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.domain.shared.exception.DomainValidationException;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler({
            ApplicationValidationException.class,
            DomainValidationException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of());
    }

    @ExceptionHandler({
            InvalidStateTransitionException.class,
            AlreadyExistsException.class
    })
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        return build(HttpStatus.BAD_REQUEST, "validation failed", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "invalid value for parameter: " + ex.getName();
        return build(HttpStatus.BAD_REQUEST, message, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "request body is malformed or contains invalid enum/date value",
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "unexpected internal error",
                Map.of()
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            Map<String, String> validationErrors
    ) {
        return ResponseEntity.status(status).body(
                new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        validationErrors
                )
        );
    }
}