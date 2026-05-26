package com.example.todo.adapter.out.kafka;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaReminderScheduledEventPublisherTest {
    private static final String TOPIC_NAME = "todo.reminder.scheduled";
    private static final Instant OCCURRED_AT = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private SimpleMeterRegistry meterRegistry;
    private KafkaReminderScheduledEventPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new KafkaReminderScheduledEventPublisher(kafkaTemplate, objectMapper, TOPIC_NAME, meterRegistry);
    }

    @Test
    void publishShouldWrapSerializationFailureInKafkaEventPublicationException() throws Exception {
        ReminderScheduledEventV1 event = event();
        JsonMappingException serializationFailure = JsonMappingException.fromUnexpectedIOE(new IOException("boom"));
        doThrow(serializationFailure).when(objectMapper).writeValueAsString(event);

        KafkaEventPublicationException exception = assertThrows(
                KafkaEventPublicationException.class,
                () -> publisher.publish(event)
        );

        assertEquals("Failed to serialize reminder scheduled event", exception.getMessage());
        assertSame(serializationFailure, exception.getCause());
        verifyNoInteractions(kafkaTemplate);
        assertEquals(
                1.0,
                meterRegistry.counter(
                        "todo.reminder.scheduled.events.publish.failures",
                        "topic", TOPIC_NAME,
                        "reason", "JsonMappingException"
                ).count()
        );
    }

    @Test
    void publishShouldWrapKafkaSendFailureInKafkaEventPublicationException() throws Exception {
        ReminderScheduledEventV1 event = event();
        RuntimeException sendFailure = new RuntimeException("broker unavailable");
        CompletableFuture<SendResult<String, String>> failedSend = CompletableFuture.failedFuture(sendFailure);
        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        when(kafkaTemplate.send(TOPIC_NAME, event.reminderId().toString(), "{}")).thenReturn(failedSend);

        KafkaEventPublicationException exception = assertThrows(
                KafkaEventPublicationException.class,
                () -> publisher.publish(event)
        );

        assertEquals("Kafka reminder scheduled event publish failed", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertSame(sendFailure, exception.getCause().getCause());
        assertEquals(
                1.0,
                meterRegistry.counter(
                        "todo.reminder.scheduled.events.publish.failures",
                        "topic", TOPIC_NAME,
                        "reason", "CompletionException"
                ).count()
        );
    }

    private static ReminderScheduledEventV1 event() {
        return new ReminderScheduledEventV1(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                OCCURRED_AT,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                OCCURRED_AT.plusSeconds(300),
                "SCHEDULED"
        );
    }
}
