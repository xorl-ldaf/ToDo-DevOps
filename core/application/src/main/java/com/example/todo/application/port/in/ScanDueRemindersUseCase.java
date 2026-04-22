package com.example.todo.application.port.in;

import java.time.Instant;

public interface ScanDueRemindersUseCase {
    ReminderProcessingReport scanAndPublishDueReminders(Instant now);
}
