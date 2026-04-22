package com.example.todo.application.port.in;

import com.example.todo.application.receipt.ReminderScheduledEventReceipt;

public interface RecordReminderScheduledEventReceiptUseCase {
    boolean record(ReminderScheduledEventReceipt receipt);
}
