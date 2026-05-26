package com.example.todo.application.factory;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReminderNotificationFactoryTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final UUID NOTIFICATION_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final ReminderNotificationFactory factory = new ReminderNotificationFactory(() -> NOTIFICATION_ID);

    @Test
    void createShouldBuildReminderNotificationFromReminderTaskAndRecipient() {
        Reminder reminder = reminder();
        Task task = task("Review rollout");
        User recipient = user(new TelegramChatId(123456789L));

        ReminderNotificationV1 notification = factory.create(reminder, task, recipient, NOW);

        assertEquals(NOTIFICATION_ID, notification.notificationId());
        assertEquals(ReminderNotificationV1.NOTIFICATION_TYPE, notification.notificationType());
        assertEquals(ReminderNotificationV1.NOTIFICATION_VERSION, notification.notificationVersion());
        assertEquals(NOW, notification.occurredAt());
        assertEquals(reminder.getId().value(), notification.reminderId());
        assertEquals(task.getId().value(), notification.taskId());
        assertEquals(task.getTitle(), notification.taskTitle());
        assertEquals(task.getDescription(), notification.taskDescription());
        assertEquals(reminder.getRemindAt(), notification.remindAt());
        assertEquals(recipient.getId().value(), notification.recipientUserId());
        assertEquals(recipient.getDisplayName(), notification.recipientDisplayName());
        assertEquals(recipient.getTelegramChatId().value(), notification.recipientTelegramChatId());
    }

    @Test
    void createShouldRejectMissingRequiredInputs() {
        Reminder reminder = reminder();
        Task task = task("Review rollout");
        User recipient = user(new TelegramChatId(123456789L));

        ApplicationValidationException reminderException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create(null, task, recipient, NOW)
        );
        ApplicationValidationException taskException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create(reminder, null, recipient, NOW)
        );
        ApplicationValidationException recipientException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create(reminder, task, null, NOW)
        );
        ApplicationValidationException nowException = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create(reminder, task, recipient, null)
        );

        assertEquals("reminder must not be null", reminderException.getMessage());
        assertEquals("task must not be null", taskException.getMessage());
        assertEquals("recipient must not be null", recipientException.getMessage());
        assertEquals("now must not be null", nowException.getMessage());
    }

    @Test
    void createShouldRejectRecipientWithoutTelegramChatId() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> factory.create(reminder(), task("Review rollout"), user(null), NOW)
        );

        assertEquals("recipientTelegramChatId must not be null", exception.getMessage());
    }

    private Reminder reminder() {
        return Reminder.restore(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                taskId("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                NOW.minusSeconds(60),
                ReminderStatus.PROCESSING,
                NOW.minusSeconds(600),
                NOW.minusSeconds(30),
                NOW.minusSeconds(60),
                NOW.minusSeconds(1),
                "worker-a",
                null,
                0,
                null
        );
    }

    private Task task(String title) {
        return new Task(
                taskId("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("11111111-1111-1111-1111-111111111111"),
                title,
                "Check prod rollout",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600)
        );
    }

    private User user(TelegramChatId telegramChatId) {
        return new User(
                userId("11111111-1111-1111-1111-111111111111"),
                "alice",
                "Alice DevOps",
                telegramChatId,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600)
        );
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
