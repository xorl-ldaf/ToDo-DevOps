package com.example.todo.application.port.in;

public record ReminderScheduledEventOutboxReport(
        int claimedCount,
        int publishedCount,
        int retriedCount,
        int failedCount,
        int concurrencyConflictCount
) {
    public static ReminderScheduledEventOutboxReport empty() {
        return new ReminderScheduledEventOutboxReport(0, 0, 0, 0, 0);
    }
}
