package com.example.todo.application.service;

import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.factory.ReminderFactory;
import com.example.todo.application.policy.TaskReferencePolicy;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.application.port.out.StoreReminderScheduledEventPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CreateReminderService implements CreateReminderUseCase {
    private final TaskReferencePolicy taskReferencePolicy;
    private final SaveReminderPort saveReminderPort;
    private final StoreReminderScheduledEventPort storeReminderScheduledEventPort;
    private final Clock clock;
    private final ReminderFactory reminderFactory;

    public CreateReminderService(
            LoadTaskPort loadTaskPort,
            SaveReminderPort saveReminderPort,
            StoreReminderScheduledEventPort storeReminderScheduledEventPort,
            Clock clock
    ) {
        this(loadTaskPort, saveReminderPort, storeReminderScheduledEventPort, clock, new ReminderFactory());
    }

    public CreateReminderService(
            LoadTaskPort loadTaskPort,
            SaveReminderPort saveReminderPort,
            StoreReminderScheduledEventPort storeReminderScheduledEventPort,
            Clock clock,
            ReminderFactory reminderFactory
    ) {
        this(new TaskReferencePolicy(loadTaskPort), saveReminderPort, storeReminderScheduledEventPort, clock, reminderFactory);
    }

    public CreateReminderService(
            TaskReferencePolicy taskReferencePolicy,
            SaveReminderPort saveReminderPort,
            StoreReminderScheduledEventPort storeReminderScheduledEventPort,
            Clock clock,
            ReminderFactory reminderFactory
    ) {
        this.taskReferencePolicy = Objects.requireNonNull(
                taskReferencePolicy,
                "taskReferencePolicy must not be null"
        );
        this.saveReminderPort = Objects.requireNonNull(saveReminderPort, "saveReminderPort must not be null");
        this.storeReminderScheduledEventPort = Objects.requireNonNull(
                storeReminderScheduledEventPort,
                "storeReminderScheduledEventPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.reminderFactory = Objects.requireNonNull(reminderFactory, "reminderFactory must not be null");
    }

    @Override
    public Reminder createReminder(CreateReminderCommand command) {
        if (command == null) {
            throw new ApplicationValidationException("command must not be null");
        }

        TaskId taskId = taskReferencePolicy.requireTaskId(command.taskId());
        if (command.remindAt() == null) {
            throw new ApplicationValidationException("remindAt must not be null");
        }
        taskReferencePolicy.requireTaskExists(taskId);

        Instant now = clock.instant();
        Reminder reminder = reminderFactory.createScheduled(taskId, command.remindAt(), now);

        Reminder savedReminder = saveReminderPort.save(reminder);
        storeReminderScheduledEventPort.store(new ReminderScheduledEventV1(
                UUID.randomUUID(),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                savedReminder.getCreatedAt(),
                savedReminder.getId().value(),
                savedReminder.getTaskId().value(),
                savedReminder.getRemindAt(),
                savedReminder.getStatus().name()
        ));

        return savedReminder;
    }
}
