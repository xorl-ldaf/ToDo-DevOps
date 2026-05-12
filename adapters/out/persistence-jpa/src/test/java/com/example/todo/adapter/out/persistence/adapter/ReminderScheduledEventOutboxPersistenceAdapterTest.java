package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxJpaEntity;
import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventOutboxStatus;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderScheduledEventOutboxRepository;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReminderScheduledEventOutboxPersistenceAdapterTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-05-12T10:00:00Z");

    @Mock
    private SpringDataReminderScheduledEventOutboxRepository repository;

    @Test
    void storeShouldPersistEventMetadataAndPayload() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        ReminderScheduledEventOutboxPersistenceAdapter adapter =
                new ReminderScheduledEventOutboxPersistenceAdapter(repository, objectMapper);
        ReminderScheduledEventV1 event = new ReminderScheduledEventV1(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                OCCURRED_AT,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                OCCURRED_AT.plusSeconds(300),
                "SCHEDULED"
        );

        adapter.store(event);

        ArgumentCaptor<ReminderScheduledEventOutboxJpaEntity> entityCaptor =
                ArgumentCaptor.forClass(ReminderScheduledEventOutboxJpaEntity.class);
        verify(repository).save(entityCaptor.capture());
        ReminderScheduledEventOutboxJpaEntity entity = entityCaptor.getValue();
        assertEquals(event.eventId(), entity.getEventId());
        assertEquals(event.reminderId(), entity.getReminderId());
        assertEquals(event.taskId(), entity.getTaskId());
        assertEquals(ReminderScheduledEventV1.EVENT_TYPE, entity.getEventType());
        assertEquals(ReminderScheduledEventV1.EVENT_VERSION, entity.getEventVersion());
        assertEquals(ReminderScheduledEventOutboxStatus.PENDING, entity.getStatus());
        assertEquals(OCCURRED_AT, entity.getCreatedAt());
        assertEquals(OCCURRED_AT, entity.getUpdatedAt());
        assertEquals(OCCURRED_AT, entity.getAvailableAt());
        assertEquals(0, entity.getDeliveryAttempts());

        JsonNode payload = objectMapper.readTree(entity.getPayload());
        assertEquals(event.eventId().toString(), payload.path("eventId").asText());
        assertEquals(ReminderScheduledEventV1.EVENT_TYPE, payload.path("eventType").asText());
        assertEquals(ReminderScheduledEventV1.EVENT_VERSION, payload.path("eventVersion").asText());
        assertEquals(event.reminderId().toString(), payload.path("reminderId").asText());
        assertEquals(event.taskId().toString(), payload.path("taskId").asText());
        assertEquals(event.remindAt().toString(), payload.path("remindAt").asText());
        assertEquals("SCHEDULED", payload.path("reminderStatus").asText());
    }
}
