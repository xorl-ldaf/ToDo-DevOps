package com.example.todo.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Objects;

public final class KafkaReminderScheduledEventPublisher implements PublishReminderScheduledEventPort {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;

    public KafkaReminderScheduledEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topicName
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.topicName = Objects.requireNonNull(topicName, "topicName must not be null");
    }

    @Override
    public void publish(ReminderScheduledEventV1 event) {
        ReminderScheduledEventV1 actualEvent = Objects.requireNonNull(event, "event must not be null");
        try {
            kafkaTemplate.send(
                    topicName,
                    actualEvent.reminderId().toString(),
                    objectMapper.writeValueAsString(actualEvent)
            ).join();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reminder scheduled event", exception);
        }
    }
}
