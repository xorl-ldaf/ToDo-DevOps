package com.example.todo.application.service;

import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class CreateReminderService implements CreateReminderUseCase {
    private final LoadTaskPort loadTaskPort;
    private final SaveReminderPort saveReminderPort;
    private final Clock clock;

    public CreateReminderService(
            LoadTaskPort loadTaskPort,
            SaveReminderPort saveReminderPort,
            Clock clock
    ) {
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort);
        this.saveReminderPort = Objects.requireNonNull(saveReminderPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Reminder createReminder(CreateReminderCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        if (loadTaskPort.loadById(command.taskId()).isEmpty()) {
            throw new IllegalArgumentException("task not found: " + command.taskId());
        }

        Instant now = clock.instant();

        if (command.remindAt() == null || command.remindAt().isBefore(now)) {
            throw new IllegalArgumentException("remindAt must not be in the past");
        }

        Reminder reminder = new Reminder(
                ReminderId.newId(),
                command.taskId(),
                command.remindAt(),
                ReminderStatus.PENDING,
                now,
                now,
                null
        );

        return saveReminderPort.save(reminder);
    }
}