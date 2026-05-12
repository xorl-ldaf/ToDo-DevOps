package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.adapter.ReminderScheduledEventReceiptPersistenceAdapter;
import com.example.todo.application.receipt.ReminderScheduledEventReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderScheduledEventReceiptRepositoryIT extends AbstractReminderPersistenceRepositoryIT {

    private ReminderScheduledEventReceiptPersistenceAdapter receiptAdapter;

    @BeforeEach
    void setUpReceiptAdapter() {
        receiptAdapter = new ReminderScheduledEventReceiptPersistenceAdapter(receiptRepository);
    }

    @Test
    void duplicateEventIdShouldReturnFalseAndKeepOriginalReceipt() {
        UUID taskId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedTask(taskId);
        adapter.save(scheduledReminder(reminderId, taskId, NOW));

        boolean firstSave = receiptAdapter.save(receipt(eventId, reminderId, taskId, 42L));
        boolean duplicateSave = receiptAdapter.save(receipt(eventId, reminderId, taskId, 43L));

        assertThat(firstSave).isTrue();
        assertThat(duplicateSave).isFalse();
        assertThat(receiptRepository.count()).isEqualTo(1L);
        assertThat(receiptRepository.findById(eventId)).hasValueSatisfying(entity ->
                assertThat(entity.getKafkaOffset()).isEqualTo(42L)
        );
    }

    private ReminderScheduledEventReceipt receipt(UUID eventId, UUID reminderId, UUID taskId, long offset) {
        return new ReminderScheduledEventReceipt(
                eventId,
                reminderId,
                taskId,
                "todo.reminder.scheduled.v1",
                "v1",
                Instant.parse("2026-05-12T09:59:55Z"),
                NOW,
                0,
                offset,
                "{\"eventId\":\"%s\"}".formatted(eventId)
        );
    }
}
