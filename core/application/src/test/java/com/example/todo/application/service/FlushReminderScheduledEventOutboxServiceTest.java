package com.example.todo.application.service;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.outbox.OutboxPublicationPolicy;
import com.example.todo.application.outbox.OutboxPublicationPolicy.PublicationFailureDecision;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private OutboxPublicationPolicy outboxPublicationPolicy;

    private FlushReminderScheduledEventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new FlushReminderScheduledEventOutboxService(
                claimReminderScheduledEventOutboxPort,
                finalizeReminderScheduledEventOutboxPort,
                publishReminderScheduledEventPort,
                PROCESSOR_ID,
                25,
                Duration.ofSeconds(30),
                outboxPublicationPolicy
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
        verifyNoInteractions(outboxPublicationPolicy);

        assertEquals(new ReminderScheduledEventOutboxReport(1, 1, 0, 0, 0), report);
    }

    @Test
    void flushShouldReturnEmptyReportWhenNoMessagesAreClaimed() {
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of());

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        assertEquals(ReminderScheduledEventOutboxReport.empty(), report);
        verify(claimReminderScheduledEventOutboxPort).claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25);
        verifyNoMoreInteractions(claimReminderScheduledEventOutboxPort);
        verifyNoInteractions(publishReminderScheduledEventPort, finalizeReminderScheduledEventOutboxPort, outboxPublicationPolicy);
    }

    @Test
    void flushShouldRejectNullNowBeforeCallingPorts() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.flush(null)
        );

        assertEquals("now must not be null", exception.getMessage());
        verifyNoInteractions(
                claimReminderScheduledEventOutboxPort,
                publishReminderScheduledEventPort,
                finalizeReminderScheduledEventOutboxPort,
                outboxPublicationPolicy
        );
    }

    @Test
    void flushShouldKeepMessageRetryableWhenPublishFailsBeforeRetryBudgetIsExhausted() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(1);
        PublicationException exception = new PublicationException("broker unavailable");
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        doThrow(exception).when(publishReminderScheduledEventPort).publish(message.event());
        when(outboxPublicationPolicy.decideFailure(message, NOW, exception))
                .thenReturn(PublicationFailureDecision.retry(NOW.plusSeconds(10), "PublicationException"));
        when(finalizeReminderScheduledEventOutboxPort.reschedule(
                message.eventId(),
                PROCESSOR_ID,
                NOW,
                NOW.plusSeconds(10),
                "PublicationException"
        )).thenReturn(true);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        verify(outboxPublicationPolicy).decideFailure(message, NOW, exception);
        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 1, 0, 0), report);
    }

    @Test
    void flushShouldCountConcurrencyConflictWhenRetryableMessageIsNotRescheduled() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(1);
        PublicationException exception = new PublicationException("broker unavailable");
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        doThrow(exception).when(publishReminderScheduledEventPort).publish(message.event());
        when(outboxPublicationPolicy.decideFailure(message, NOW, exception))
                .thenReturn(PublicationFailureDecision.retry(NOW.plusSeconds(10), "PublicationException"));
        when(finalizeReminderScheduledEventOutboxPort.reschedule(
                message.eventId(),
                PROCESSOR_ID,
                NOW,
                NOW.plusSeconds(10),
                "PublicationException"
        )).thenReturn(false);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 0, 0, 1), report);
    }

    @Test
    void flushShouldMarkMessageFailedWhenRetryBudgetIsExhausted() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(4);
        PublicationException exception = new PublicationException("broker unavailable");
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        doThrow(exception).when(publishReminderScheduledEventPort).publish(message.event());
        when(outboxPublicationPolicy.decideFailure(message, NOW, exception))
                .thenReturn(PublicationFailureDecision.failed("PublicationException"));
        when(finalizeReminderScheduledEventOutboxPort.markFailed(
                message.eventId(),
                PROCESSOR_ID,
                NOW,
                "PublicationException"
        )).thenReturn(true);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        verify(outboxPublicationPolicy).decideFailure(message, NOW, exception);
        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 0, 1, 0), report);
    }

    @Test
    void flushShouldCountConcurrencyConflictWhenFailedMessageIsNotFinalized() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(4);
        PublicationException exception = new PublicationException("broker unavailable");
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        doThrow(exception).when(publishReminderScheduledEventPort).publish(message.event());
        when(outboxPublicationPolicy.decideFailure(message, NOW, exception))
                .thenReturn(PublicationFailureDecision.failed("PublicationException"));
        when(finalizeReminderScheduledEventOutboxPort.markFailed(
                message.eventId(),
                PROCESSOR_ID,
                NOW,
                "PublicationException"
        )).thenReturn(false);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 0, 0, 1), report);
    }

    @Test
    void flushShouldCountConcurrencyConflictWhenPublishedMessageIsNotFinalized() {
        ReminderScheduledEventOutboxMessage message = outboxMessage(0);
        when(claimReminderScheduledEventOutboxPort.claimPending(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(message));
        when(finalizeReminderScheduledEventOutboxPort.markPublished(message.eventId(), PROCESSOR_ID, NOW)).thenReturn(false);

        ReminderScheduledEventOutboxReport report = service.flush(NOW);

        verifyNoInteractions(outboxPublicationPolicy);
        assertEquals(new ReminderScheduledEventOutboxReport(1, 0, 0, 0, 1), report);
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

    private static final class PublicationException extends RuntimeException {
        private PublicationException(String message) {
            super(message);
        }
    }
}
