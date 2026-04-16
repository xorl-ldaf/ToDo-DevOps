package com.example.todo.application.exception;

public class AlreadyExistsException extends ApplicationException {
    public AlreadyExistsException(String message) {
        super(message);
    }
}
