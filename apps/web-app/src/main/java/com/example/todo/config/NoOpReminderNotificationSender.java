package com.example.todo.config;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;

public final class NoOpReminderNotificationSender implements DeliverReminderNotificationPort {

    @Override
    public ReminderNotificationDeliveryResult deliver(ReminderNotificationV1 notification) {
        // Telegram delivery stays opt-in. When disabled, due reminders remain pending
        // instead of being silently marked as published by a fake sender.
        return ReminderNotificationDeliveryResult.skipped("telegram delivery is disabled");
    }
}
