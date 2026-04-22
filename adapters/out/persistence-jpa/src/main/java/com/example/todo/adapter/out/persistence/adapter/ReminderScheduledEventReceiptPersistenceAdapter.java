package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventReceiptJpaEntity;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderScheduledEventReceiptRepository;
import com.example.todo.application.port.out.SaveReminderScheduledEventReceiptPort;
import com.example.todo.application.receipt.ReminderScheduledEventReceipt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.NestedExceptionUtils;

import java.sql.SQLException;
import java.util.Objects;

public class ReminderScheduledEventReceiptPersistenceAdapter implements SaveReminderScheduledEventReceiptPort {
    private final SpringDataReminderScheduledEventReceiptRepository repository;

    public ReminderScheduledEventReceiptPersistenceAdapter(
            SpringDataReminderScheduledEventReceiptRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public boolean save(ReminderScheduledEventReceipt receipt) {
        ReminderScheduledEventReceipt actualReceipt = Objects.requireNonNull(receipt, "receipt must not be null");

        try {
            ReminderScheduledEventReceiptJpaEntity entity = new ReminderScheduledEventReceiptJpaEntity();
            entity.setEventId(actualReceipt.eventId());
            entity.setReminderId(actualReceipt.reminderId());
            entity.setTaskId(actualReceipt.taskId());
            entity.setTopic(actualReceipt.topic());
            entity.setEventVersion(actualReceipt.eventVersion());
            entity.setOccurredAt(actualReceipt.occurredAt());
            entity.setConsumedAt(actualReceipt.consumedAt());
            entity.setKafkaPartition(actualReceipt.partition());
            entity.setKafkaOffset(actualReceipt.offset());
            entity.setPayload(actualReceipt.payload());
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateKeyViolation(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException exception) {
        Throwable mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(exception);
        return mostSpecificCause instanceof SQLException sqlException
                && "23505".equals(sqlException.getSQLState());
    }
}
