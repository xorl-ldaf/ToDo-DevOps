package com.example.todo.application.port.out;

import com.example.todo.domain.reminder.Reminder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

public interface ClaimDueRemindersPort {
    List<Reminder> claimDueReminders(
            Instant now,
            Duration processingTimeout,
            int limit,
            UnaryOperator<Reminder> claimTransition
    );
}
