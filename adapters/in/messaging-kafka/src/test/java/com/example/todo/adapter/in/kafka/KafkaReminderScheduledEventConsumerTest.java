package com.example.todo.adapter.in.kafka;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.in.RecordReminderScheduledEventReceiptUseCase;
import com.example.todo.application.receipt.ReminderScheduledEventReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaReminderScheduledEventConsumerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-05-12T10:00:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-05-12T10:00:05Z");
    private static final String TOPIC = "todo.reminder.scheduled.v1";

    @Mock
    private RecordReminderScheduledEventReceiptUseCase recordReminderScheduledEventReceiptUseCase;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private KafkaReminderScheduledEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        meterRegistry = new SimpleMeterRegistry();
        consumer = new KafkaReminderScheduledEventConsumer(
                objectMapper,
                meterRegistry,
                Clock.fixed(CONSUMED_AT, ZoneOffset.UTC),
                recordReminderScheduledEventReceiptUseCase
        );
    }

    @Test
    void consumeShouldRecordDuplicateEventAsDuplicateReceiptWithoutFailing() throws Exception {
        ReminderScheduledEventV1 event = event();
        String payload = objectMapper.writeValueAsString(event);
        when(recordReminderScheduledEventReceiptUseCase.record(any())).thenReturn(true, false);

        consumer.consume(payload, TOPIC, event.reminderId().toString(), 0, 42L);
        consumer.consume(payload, TOPIC, event.reminderId().toString(), 0, 43L);

        ArgumentCaptor<ReminderScheduledEventReceipt> receiptCaptor =
                ArgumentCaptor.forClass(ReminderScheduledEventReceipt.class);
        verify(recordReminderScheduledEventReceiptUseCase, times(2)).record(receiptCaptor.capture());

        List<ReminderScheduledEventReceipt> receipts = receiptCaptor.getAllValues();
        assertEquals(event.eventId(), receipts.get(0).eventId());
        assertEquals(event.eventId(), receipts.get(1).eventId());
        assertEquals(42L, receipts.get(0).offset());
        assertEquals(43L, receipts.get(1).offset());
        assertEquals(CONSUMED_AT, receipts.get(0).consumedAt());
        assertEquals(payload, receipts.get(0).payload());

        assertEquals(2.0d, counter("todo.reminder.scheduled.events.consumed", "event_version", "v1").count());
        assertEquals(1.0d, counter("todo.reminder.scheduled.events.receipts", "outcome", "stored").count());
        assertEquals(1.0d, counter("todo.reminder.scheduled.events.receipts", "outcome", "duplicate").count());
        Timer lagTimer = meterRegistry.find("todo.reminder.scheduled.event.consume.lag")
                .tags("topic", TOPIC, "event_version", "v1")
                .timer();
        assertNotNull(lagTimer);
        assertEquals(2L, lagTimer.count());
    }

    private Counter counter(String name, String tagKey, String tagValue) {
        Counter counter = meterRegistry.find(name)
                .tags("topic", TOPIC, tagKey, tagValue)
                .counter();
        assertNotNull(counter);
        assertTrue(counter.count() > 0.0d);
        return counter;
    }

    private ReminderScheduledEventV1 event() {
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
