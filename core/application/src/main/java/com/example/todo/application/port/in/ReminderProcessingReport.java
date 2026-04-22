package com.example.todo.application.port.in;

public record ReminderProcessingReport(
        int claimedCount,
        int deliveredCount,
        int retriedCount,
        int failedCount,
        int concurrencyConflictCount
) {
    public static ReminderProcessingReport empty() {
        return new ReminderProcessingReport(0, 0, 0, 0, 0);
    }
}
