package com.example.todo.application.exception;

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}