package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.mapper.ReminderPersistenceMapper;

import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.application.port.out.LoadDueRemindersPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.time.Instant;
import java.util.List;

public class ReminderPersistenceAdapter implements LoadDueRemindersPort, SaveReminderPort, LoadTaskRemindersPort {

    private final SpringDataReminderRepository repository;
    private final int dueReminderBatchSize;

    public ReminderPersistenceAdapter(SpringDataReminderRepository repository, int dueReminderBatchSize) {
        this.repository = repository;
        this.dueReminderBatchSize = dueReminderBatchSize;
    }

    @Override
    public List<Reminder> loadDueReminders(Instant now) {
        return repository.findDuePendingForDelivery(now, dueReminderBatchSize)
                .stream()
                .map(ReminderPersistenceMapper::toDomain)
                .toList();
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
}
