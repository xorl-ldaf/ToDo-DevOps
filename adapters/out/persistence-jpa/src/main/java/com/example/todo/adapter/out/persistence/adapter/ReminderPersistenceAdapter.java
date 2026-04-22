package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.mapper.ReminderPersistenceMapper;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.task.TaskId;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReminderPersistenceAdapter implements
        ClaimDueRemindersPort,
        FinalizeReminderDeliveryPort,
        SaveReminderPort,
        LoadTaskRemindersPort {

    private final SpringDataReminderRepository repository;

    public ReminderPersistenceAdapter(SpringDataReminderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public List<Reminder> claimDueReminders(Instant now, String processorId, Duration processingTimeout, int limit) {
        List<Reminder> claimedReminders = new ArrayList<>();
        for (Reminder reminder : repository.findClaimableForProcessing(now, now.minus(processingTimeout), limit)
                .stream()
                .map(ReminderPersistenceMapper::toDomain)
                .toList()) {
            reminder.markProcessing(processorId, now);
            claimedReminders.add(save(reminder));
        }
        return claimedReminders;
    }

    @Override
    public List<Reminder> loadByTaskId(TaskId taskId) {
        return repository.findByTaskIdOrderByRemindAtAsc(taskId.value())
                .stream()
                .map(ReminderPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Reminder save(Reminder reminder) {
        return ReminderPersistenceMapper.toDomain(
                repository.save(ReminderPersistenceMapper.toJpa(reminder))
        );
    }

    @Override
    @Transactional
    public boolean markDelivered(ReminderId reminderId, String processorId, Instant deliveredAt) {
        return withClaimedReminder(reminderId, processorId, reminder -> reminder.markDelivered(deliveredAt));
    }

    @Override
    @Transactional
    public boolean reschedule(
            ReminderId reminderId,
            String processorId,
            Instant processedAt,
            Instant nextAttemptAt,
            String failureReason
    ) {
        return withClaimedReminder(
                reminderId,
                processorId,
                reminder -> reminder.reschedule(processedAt, nextAttemptAt, failureReason)
        );
    }

    @Override
    @Transactional
    public boolean markFailed(ReminderId reminderId, String processorId, Instant processedAt, String failureReason) {
        return withClaimedReminder(reminderId, processorId, reminder -> reminder.markFailed(processedAt, failureReason));
    }

    private boolean withClaimedReminder(ReminderId reminderId, String processorId, Consumer<Reminder> action) {
        return repository.findForUpdateByIdAndStatusAndProcessingOwner(
                        reminderId.value(),
                        "PROCESSING",
                        processorId
                )
                .map(ReminderPersistenceMapper::toDomain)
                .map(reminder -> {
                    action.accept(reminder);
                    save(reminder);
                    return true;
                })
                .orElse(false);
    }
}
