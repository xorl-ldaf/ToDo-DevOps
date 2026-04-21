package com.example.todo.application.service;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import com.example.todo.application.port.out.LoadDueRemindersPort;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ScanDueRemindersService implements ScanDueRemindersUseCase {
    private final LoadDueRemindersPort loadDueRemindersPort;
    private final LoadTaskPort loadTaskPort;
    private final LoadUserDetailsPort loadUserDetailsPort;
    private final DeliverReminderNotificationPort deliverReminderNotificationPort;
    private final SaveReminderPort saveReminderPort;

    public ScanDueRemindersService(
            LoadDueRemindersPort loadDueRemindersPort,
            LoadTaskPort loadTaskPort,
            LoadUserDetailsPort loadUserDetailsPort,
            DeliverReminderNotificationPort deliverReminderNotificationPort,
            SaveReminderPort saveReminderPort
    ) {
        this.loadDueRemindersPort = Objects.requireNonNull(loadDueRemindersPort, "loadDueRemindersPort must not be null");
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
        this.loadUserDetailsPort = Objects.requireNonNull(loadUserDetailsPort, "loadUserDetailsPort must not be null");
        this.deliverReminderNotificationPort = Objects.requireNonNull(
                deliverReminderNotificationPort,
                "deliverReminderNotificationPort must not be null"
        );
        this.saveReminderPort = Objects.requireNonNull(saveReminderPort, "saveReminderPort must not be null");
    }

    @Override
    public int scanAndPublishDueReminders(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        List<Reminder> reminders = loadDueRemindersPort.loadDueReminders(now);
        int publishedCount = 0;

        for (Reminder reminder : reminders) {
            if (!reminder.isDueAt(now)) {
                continue;
            }

            Task task = loadTaskPort.loadById(reminder.getTaskId()).orElse(null);
            if (task == null) {
                continue;
            }

            User recipient = loadUserDetailsPort.loadById(task.getAssigneeId()).orElse(null);
            if (recipient == null || recipient.getTelegramChatId() == null) {
                continue;
            }

            ReminderNotificationDeliveryResult deliveryResult = deliverReminderNotificationPort.deliver(
                    new ReminderNotificationV1(
                            UUID.randomUUID(),
                            ReminderNotificationV1.NOTIFICATION_TYPE,
                            ReminderNotificationV1.NOTIFICATION_VERSION,
                            now,
                            reminder.getId().value(),
                            task.getId().value(),
                            task.getTitle(),
                            task.getDescription(),
                            reminder.getRemindAt(),
                            recipient.getId().value(),
                            recipient.getDisplayName(),
                            recipient.getTelegramChatId().value()
                    )
            );
            if (!deliveryResult.deliveredSuccessfully()) {
                continue;
            }

            reminder.markPublished(now);
            saveReminderPort.save(reminder);
            publishedCount++;
        }

        return publishedCount;
    }
}
