package com.example.todo.application.service;

import com.example.todo.application.port.in.RecordReminderScheduledEventReceiptUseCase;
import com.example.todo.application.port.out.SaveReminderScheduledEventReceiptPort;
import com.example.todo.application.receipt.ReminderScheduledEventReceipt;

import java.util.Objects;

public class RecordReminderScheduledEventReceiptService implements RecordReminderScheduledEventReceiptUseCase {
    private final SaveReminderScheduledEventReceiptPort saveReminderScheduledEventReceiptPort;

    public RecordReminderScheduledEventReceiptService(
            SaveReminderScheduledEventReceiptPort saveReminderScheduledEventReceiptPort
    ) {
        this.saveReminderScheduledEventReceiptPort = Objects.requireNonNull(
                saveReminderScheduledEventReceiptPort,
                "saveReminderScheduledEventReceiptPort must not be null"
        );
    }

    @Override
    public boolean record(ReminderScheduledEventReceipt receipt) {
        return saveReminderScheduledEventReceiptPort.save(receipt);
    }
}
