package com.example.todo.application.port.out;

import com.example.todo.application.notification.ReminderNotificationV1;

public interface DeliverReminderNotificationPort {
    ReminderNotificationDeliveryResult deliver(ReminderNotificationV1 notification);
}
