package com.example.todo.application.exception;

public class InvalidStateTransitionException extends ApplicationException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
