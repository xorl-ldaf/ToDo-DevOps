package com.example.todo.application.port.in;

import java.time.Instant;

public interface ScanDueRemindersUseCase {
    int scanAndPublishDueReminders(Instant now);
}