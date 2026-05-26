package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.user.User;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class ReminderNotificationFactory {
    private final Supplier<UUID> notificationIdSupplier;

    public ReminderNotificationFactory() {
        this(UUID::randomUUID);
    }

    ReminderNotificationFactory(Supplier<UUID> notificationIdSupplier) {
        this.notificationIdSupplier = Objects.requireNonNull(
                notificationIdSupplier,
                "notificationIdSupplier must not be null"
        );
    }

    public ReminderNotificationV1 create(Reminder reminder, Task task, User recipient, Instant now) {
        Reminder actualReminder = requireNonNull(reminder, "reminder");
        Task actualTask = requireNonNull(task, "task");
        User actualRecipient = requireNonNull(recipient, "recipient");
        Instant occurredAt = requireNonNull(now, "now");
        TelegramChatId telegramChatId = requireNonNull(
                actualRecipient.getTelegramChatId(),
                "recipientTelegramChatId"
        );

        return new ReminderNotificationV1(
                notificationIdSupplier.get(),
                ReminderNotificationV1.NOTIFICATION_TYPE,
                ReminderNotificationV1.NOTIFICATION_VERSION,
                occurredAt,
                actualReminder.getId().value(),
                actualTask.getId().value(),
                actualTask.getTitle(),
                actualTask.getDescription(),
                actualReminder.getRemindAt(),
                actualRecipient.getId().value(),
                actualRecipient.getDisplayName(),
                telegramChatId.value()
        );
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
