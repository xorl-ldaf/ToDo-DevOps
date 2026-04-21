package com.example.todo.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.todo.application.event.ReminderScheduledEventV1;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class KafkaReminderScheduledEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaReminderScheduledEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public KafkaReminderScheduledEventConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @KafkaListener(
            id = "reminderScheduledV1Consumer",
            idIsGroup = false,
            groupId = "${todo.kafka.consumer-group-id}",
            topics = "${todo.kafka.topics.reminder-scheduled-v1}",
            containerFactory = "reminderScheduledKafkaListenerContainerFactory"
    )
    public void consume(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        String actualPayload = Objects.requireNonNull(payload, "payload must not be null");
        ReminderScheduledEventV1 actualEvent;
        try {
            actualEvent = objectMapper.readValue(actualPayload, ReminderScheduledEventV1.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize reminder scheduled event", exception);
        }

        meterRegistry.counter(
                "todo.reminder.scheduled.events.consumed",
                "topic", topic,
                "event_version", actualEvent.eventVersion()
        ).increment();

        Duration consumeLag = Duration.between(actualEvent.occurredAt(), clock.instant());
        if (!consumeLag.isNegative()) {
            meterRegistry.timer(
                    "todo.reminder.scheduled.event.consume.lag",
                    "topic", topic,
                    "event_version", actualEvent.eventVersion()
            ).record(consumeLag);
        }

        log.info(
                "Consumed reminder scheduled event eventId={} reminderId={} taskId={} topic={} key={} remindAt={}",
                actualEvent.eventId(),
                actualEvent.reminderId(),
                actualEvent.taskId(),
                topic,
                key,
                actualEvent.remindAt()
        );
    }
}
