package com.example.todo.application.service;

import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import com.example.todo.application.port.out.LoadDueRemindersPort;
import com.example.todo.application.port.out.PublishReminderEventPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ScanDueRemindersService implements ScanDueRemindersUseCase {
    private final LoadDueRemindersPort loadDueRemindersPort;
    private final PublishReminderEventPort publishReminderEventPort;
    private final SaveReminderPort saveReminderPort;

    public ScanDueRemindersService(
            LoadDueRemindersPort loadDueRemindersPort,
            PublishReminderEventPort publishReminderEventPort,
            SaveReminderPort saveReminderPort
    ) {
        this.loadDueRemindersPort = Objects.requireNonNull(loadDueRemindersPort, "loadDueRemindersPort must not be null");
        this.publishReminderEventPort = Objects.requireNonNull(publishReminderEventPort, "publishReminderEventPort must not be null");
        this.saveReminderPort = Objects.requireNonNull(saveReminderPort, "saveReminderPort must not be null");
    }

    @Override
    public int scanAndPublishDueReminders(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        List<Reminder> reminders = loadDueRemindersPort.loadDueReminders(now);

        for (Reminder reminder : reminders) {
            if (!reminder.isDueAt(now)) {
                continue;
            }

            publishReminderEventPort.publish(reminder);
            reminder.markPublished(now);
            saveReminderPort.save(reminder);
        }

        return reminders.size();
    }
}