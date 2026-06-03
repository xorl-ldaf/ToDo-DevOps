package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.adapter.out.persistence.mapper.ReminderPersistenceMapper;
import com.example.todo.adapter.out.persistence.exception.PersistenceAdapterFailures;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @Transactional
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
    public PageResult<Reminder> loadByTaskId(TaskId taskId, PageQuery pageQuery, ReminderStatus status) {
        return PersistenceAdapterFailures.execute(
                "Load task reminders",
                () -> toPageResult(loadPage(taskId, pageQuery, status))
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
    @Transactional
    public boolean finalizeDelivery(Reminder reminder, String processorId) {
        Reminder actualReminder = Objects.requireNonNull(reminder, "reminder must not be null");
        return PersistenceAdapterFailures.execute(
                "Finalize reminder delivery",
                () -> repository.findForUpdateByIdAndStatusAndProcessingOwner(
                                actualReminder.getId().value(),
                                ReminderStatus.PROCESSING,
                                processorId
                        )
                        .map(lockedReminder -> {
                            save(actualReminder);
                            return true;
                        })
                .orElse(false)
        );
    }

    private Page<ReminderJpaEntity> loadPage(TaskId taskId, PageQuery pageQuery, ReminderStatus status) {
        Pageable pageable = toPageable(pageQuery);
        if (status == null) {
            return repository.findByTaskId(taskId.value(), pageable);
        }
        return repository.findByTaskIdAndStatus(taskId.value(), status, pageable);
    }

    private static PageResult<Reminder> toPageResult(Page<ReminderJpaEntity> page) {
        return new PageResult<>(
                page.getContent().stream().map(ReminderPersistenceMapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private static Pageable toPageable(PageQuery pageQuery) {
        return PageRequest.of(
                pageQuery.page(),
                pageQuery.size(),
                toSort(pageQuery.sort(), Sort.by(Sort.Direction.ASC, "remindAt"))
        );
    }

    private static Sort toSort(String sort, Sort defaultSort) {
        if (sort == null) {
            return defaultSort;
        }

        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new ApplicationValidationException("sort must use format field,direction");
        }

        String property = Map.of(
                "id", "id",
                "remindAt", "remindAt",
                "status", "status",
                "createdAt", "createdAt",
                "updatedAt", "updatedAt",
                "deliveredAt", "deliveredAt"
        ).get(parts[0].trim());
        if (property == null) {
            throw new ApplicationValidationException("unsupported sort field: " + parts[0].trim());
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new ApplicationValidationException("unsupported sort direction: " + parts[1].trim()));
        }

        return Sort.by(direction, property);
    }
}
