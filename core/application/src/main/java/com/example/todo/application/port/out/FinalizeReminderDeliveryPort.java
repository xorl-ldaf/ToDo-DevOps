package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;

public interface FinalizeReminderDeliveryPort {
    boolean finalizeDelivery(Reminder reminder, String processorId);
}
