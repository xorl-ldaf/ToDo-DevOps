package com.example.todo.application.port.in;

import java.time.Instant;

public interface ScanDueRemindersUseCase {
    ReminderProcessingReport processDueReminders(Instant now);

    @Deprecated(forRemoval = false)
    default ReminderProcessingReport scanAndPublishDueReminders(Instant now) {
        return processDueReminders(now);
    }
}
