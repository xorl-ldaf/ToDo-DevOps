package com.example.todo.adapter.out.kafka;

public class KafkaEventPublicationException extends RuntimeException {
    public KafkaEventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
