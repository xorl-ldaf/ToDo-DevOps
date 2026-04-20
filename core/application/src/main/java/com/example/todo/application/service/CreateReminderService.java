package com.example.todo.application.service;

import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;

import java.time.Clock;
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
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
        this.saveReminderPort = Objects.requireNonNull(saveReminderPort, "saveReminderPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Reminder createReminder(CreateReminderCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        if (command.taskId() == null) {
            throw new ApplicationValidationException("taskId must not be null");
        }
        if (command.remindAt() == null) {
            throw new ApplicationValidationException("remindAt must not be null");
        }

        if (loadTaskPort.loadById(command.taskId()).isEmpty()) {
            throw new ResourceNotFoundException("task not found: " + command.taskId().value());
        }

        Reminder reminder = Reminder.schedule(
                command.taskId(),
                command.remindAt(),
                clock.instant()
        );

        return saveReminderPort.save(reminder);
    }
}
