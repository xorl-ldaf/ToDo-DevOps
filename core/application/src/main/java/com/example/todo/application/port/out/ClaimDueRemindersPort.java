package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface ClaimDueRemindersPort {
    List<Reminder> claimDueReminders(Instant now, String processorId, Duration processingTimeout, int limit);
}
