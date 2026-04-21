package com.example.todo.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Objects;

public final class KafkaReminderScheduledEventPublisher implements PublishReminderScheduledEventPort {
    private static final Logger log = LoggerFactory.getLogger(KafkaReminderScheduledEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;
    private final MeterRegistry meterRegistry;

    public KafkaReminderScheduledEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topicName,
            MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.topicName = Objects.requireNonNull(topicName, "topicName must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public void publish(ReminderScheduledEventV1 event) {
        ReminderScheduledEventV1 actualEvent = Objects.requireNonNull(event, "event must not be null");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            kafkaTemplate.send(
                    topicName,
                    actualEvent.reminderId().toString(),
                    objectMapper.writeValueAsString(actualEvent)
            ).join();
            sample.stop(meterRegistry.timer(
                    "todo.reminder.scheduled.events.publish.duration",
                    "topic", topicName,
                    "outcome", "success"
            ));
            meterRegistry.counter(
                    "todo.reminder.scheduled.events.published",
                    "topic", topicName
            ).increment();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reminder scheduled event", exception);
        } catch (RuntimeException exception) {
            sample.stop(meterRegistry.timer(
                    "todo.reminder.scheduled.events.publish.duration",
                    "topic", topicName,
                    "outcome", "failure"
            ));
            meterRegistry.counter(
                    "todo.reminder.scheduled.events.publish.failures",
                    "topic", topicName,
                    "reason", exception.getClass().getSimpleName()
            ).increment();
            log.error(
                    "Kafka reminder scheduled event publish failed eventId={} reminderId={} topic={}",
                    actualEvent.eventId(),
                    actualEvent.reminderId(),
                    topicName,
                    exception
            );
        }
    }
}
