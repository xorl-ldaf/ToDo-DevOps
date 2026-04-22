package com.example.todo.application.port.out;

import com.example.todo.application.receipt.ReminderScheduledEventReceipt;

public interface SaveReminderScheduledEventReceiptPort {
    boolean save(ReminderScheduledEventReceipt receipt);
}
