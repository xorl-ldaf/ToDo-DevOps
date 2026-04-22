package com.example.todo.application.service;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
import com.example.todo.application.port.in.ReminderScheduledEventOutboxReport;
import com.example.todo.application.port.out.ClaimReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.FinalizeReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlushReminderScheduledEventOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final String PROCESSOR_ID = "outbox-worker-a";

    @Mock
    private ClaimReminderScheduledEventOutboxPort claimReminderScheduledEventOutboxPort;

    @Mock
    private FinalizeReminderScheduledEventOutboxPort finalizeReminderScheduledEventOutboxPort;

    @Mock
    private PublishReminderScheduledEventPort publishReminderScheduledEventPort;

    private FlushReminderScheduledEventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new FlushReminderScheduledEventOutboxService(
                claimReminderScheduledEventOutboxPort,
                finalizeReminderScheduledEventOutboxPort,
                publishReminderScheduledEventPort,
                PROCESSOR_ID,
                25,
                5,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30)
        );
    }

    @Test
    void flushShouldPublishAndFinalizeClaimedMessage() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(0);
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        when(finalizeReminderScheduledEventOutboxPort.markPublished(message.eventId(), PROCESSOR_ID, NOW)).thenReturn(true);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        InOrder inOrder = inOrder(
                claimReminderScheduledEventOutboxPort,
                publishReminderScheduledEventPort,
                finalizeReminderScheduledEventOutboxPort
        );
        inOrder.verify(claimReminderScheduledEventOutboxPort).claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25);
        inOrder.verify(publishReminderScheduledEventPort).publish(message.event());
        inOrder.verify(finalizeReminderScheduledEventOutboxPort).markPublished(message.eventId(), PROCESSOR_ID, NOW);
        verifyNoMoreInteractions(
                claimReminderScheduledEventOutboxPort,
                publishReminderScheduledEventPort,
                finalizeReminderScheduledEventOutboxPort
        );

        assertEquals(new ReminderScheduledEventOutboxReport(1, 1, 0, 0, 0), report);
    }

    @Test
    void flushShouldKeepMessageRetryableWhenPublishFailsBeforeRetryBudgetIsExhausted() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(1);
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publishReminderScheduledEventPort).publish(message.event());
        when(finalizeReminderScheduledEventOutboxPort.reschedule(
                message.eventId(),
                PROCESSOR_ID,
                NOW,
                NOW.plusSeconds(10),
                "IllegalStateException"
        )).thenReturn(true);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 1, 0, 0), report);
    }

    @Test
    void flushShouldMarkMessageFailedWhenRetryBudgetIsExhausted() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(4);
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publishReminderScheduledEventPort).publish(message.event());
        when(finalizeReminderScheduledEventOutboxPort.markFailed(
                message.eventId(),
                PROCESSOR_ID,
                NOW,
                "IllegalStateException"
        )).thenReturn(true);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 0, 1, 0), report);
    }

    private ReminderScheduledEventOutboxMessage outboxMessage(int deliveryAttempts) {
        ReminderScheduledEventV1 event = new ReminderScheduledEventV1(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                NOW,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                NOW.plusSeconds(300),
                "SCHEDULED"
        );
        return new ReminderScheduledEventOutboxMessage(event.eventId(), event, deliveryAttempts, NOW);
    }
}
