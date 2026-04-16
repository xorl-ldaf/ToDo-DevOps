package com.example.todo.domain.shared.exception;

public class InvalidStateTransitionException extends DomainException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}