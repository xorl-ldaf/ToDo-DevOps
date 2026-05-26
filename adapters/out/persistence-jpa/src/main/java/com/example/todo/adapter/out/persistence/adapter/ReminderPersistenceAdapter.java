package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.mapper.ReminderPersistenceMapper;
import com.example.todo.adapter.out.persistence.exception.PersistenceAdapterFailures;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class ReminderPersistenceAdapter implements
        ClaimDueRemindersPort,
        FinalizeReminderDeliveryPort,
        SaveReminderPort,
        LoadTaskRemindersPort {

    private final SpringDataReminderRepository repository;

    public ReminderPersistenceAdapter(SpringDataReminderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public List<Reminder> claimDueReminders(
            Instant now,
            Duration processingTimeout,
            int limit,
            UnaryOperator<Reminder> claimTransition
    ) {
        UnaryOperator<Reminder> actualClaimTransition = Objects.requireNonNull(
                claimTransition,
                "claimTransition must not be null"
        );
        List<Reminder> claimedReminders = new ArrayList<>();
        List<Reminder> claimableReminders = PersistenceAdapterFailures.execute(
                "Claim due reminders",
                () -> repository.findClaimableForProcessing(now, now.minus(processingTimeout), limit)
                        .stream()
                        .map(ReminderPersistenceMapper::toDomain)
                        .toList()
        );
        for (Reminder reminder : claimableReminders) {
            claimedReminders.add(save(actualClaimTransition.apply(reminder)));
        }
        return claimedReminders;
    }

    @Override
    public List<Reminder> loadByTaskId(TaskId taskId) {
        return PersistenceAdapterFailures.execute(
                "Load task reminders",
                () -> repository.findByTaskIdOrderByRemindAtAsc(taskId.value())
                        .stream()
                        .map(ReminderPersistenceMapper::toDomain)
                        .toList()
        );
    }

    @Override
    public Reminder save(Reminder reminder) {
        return PersistenceAdapterFailures.execute(
                "Save reminder",
                () -> ReminderPersistenceMapper.toDomain(
                        repository.save(ReminderPersistenceMapper.toJpa(reminder))
                )
        );
    }

    @Override
    public boolean finalizeDelivery(Reminder reminder, String processorId) {
        Reminder actualReminder = Objects.requireNonNull(reminder, "reminder must not be null");
        return PersistenceAdapterFailures.execute(
                "Finalize reminder delivery",
                () -> repository.findForUpdateByIdAndStatusAndProcessingOwner(
                                actualReminder.getId().value(),
                                ReminderStatus.PROCESSING.name(),
                                processorId
                        )
                        .map(lockedReminder -> {
                            save(actualReminder);
                            return true;
                        })
                        .orElse(false)
        );
    }
}
