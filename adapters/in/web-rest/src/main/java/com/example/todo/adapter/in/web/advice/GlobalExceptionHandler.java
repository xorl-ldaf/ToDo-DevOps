package com.example.todo.adapter.in.web.advice;

import com.example.todo.adapter.in.web.dto.ApiError;
import com.example.todo.adapter.in.web.dto.ApiErrorCode;
import com.example.todo.application.exception.AlreadyExistsException;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import com.example.todo.application.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "resource not found", request, Map.of());
    }

    @ExceptionHandler(ApplicationValidationException.class)
    public ResponseEntity<ApiError> handleBadRequest(ApplicationValidationException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.APPLICATION_VALIDATION_FAILED,
                ex.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAlreadyExists(AlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ApiErrorCode.ALREADY_EXISTS, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidStateTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ApiErrorCode.INVALID_STATE, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, "validation failed", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage())
        );

        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, "validation failed", request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String message = "invalid value for parameter: " + ex.getName();
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message, request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "request body is malformed or contains invalid enum/date value",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while handling request", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "unexpected internal error",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            ApiErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(
                new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        errorCode,
                        message,
                        request.getRequestURI(),
                        fieldErrors
                )
        );
    }
}
