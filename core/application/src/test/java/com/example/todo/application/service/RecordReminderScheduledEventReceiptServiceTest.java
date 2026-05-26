package com.example.todo.application.service;

import com.example.todo.application.port.out.SaveReminderScheduledEventReceiptPort;
import com.example.todo.application.receipt.ReminderScheduledEventReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordReminderScheduledEventReceiptServiceTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-04-20T10:00:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-04-20T10:00:03Z");

    @Mock
    private SaveReminderScheduledEventReceiptPort saveReminderScheduledEventReceiptPort;

    private RecordReminderScheduledEventReceiptService service;

    @BeforeEach
    void setUp() {
        service = new RecordReminderScheduledEventReceiptService(saveReminderScheduledEventReceiptPort);
    }

    @Test
    void recordShouldReturnTrueWhenReceiptIsSavedForFirstDelivery() {
        ReminderScheduledEventReceipt receipt = receipt();
        when(saveReminderScheduledEventReceiptPort.save(receipt)).thenReturn(true);

        boolean recorded = service.record(receipt);

        assertTrue(recorded);
        verify(saveReminderScheduledEventReceiptPort).save(receipt);
        verifyNoMoreInteractions(saveReminderScheduledEventReceiptPort);
    }

    @Test
    void recordShouldReturnFalseWhenReceiptAlreadyExists() {
        ReminderScheduledEventReceipt receipt = receipt();
        when(saveReminderScheduledEventReceiptPort.save(receipt)).thenReturn(false);

        boolean recorded = service.record(receipt);

        assertFalse(recorded);
        verify(saveReminderScheduledEventReceiptPort).save(receipt);
        verifyNoMoreInteractions(saveReminderScheduledEventReceiptPort);
    }

    private ReminderScheduledEventReceipt receipt() {
        return new ReminderScheduledEventReceipt(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "todo.reminder-scheduled",
                "1",
                OCCURRED_AT,
                CONSUMED_AT,
                2,
                42L,
                "{\"eventId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}"
        );
    }
}
