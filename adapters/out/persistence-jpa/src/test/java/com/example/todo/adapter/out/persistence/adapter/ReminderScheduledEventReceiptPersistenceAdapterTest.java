package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.ReminderScheduledEventReceiptJpaEntity;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderScheduledEventReceiptRepository;
import com.example.todo.application.receipt.ReminderScheduledEventReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReminderScheduledEventReceiptPersistenceAdapterTest {

    @Mock
    private SpringDataReminderScheduledEventReceiptRepository repository;

    private ReminderScheduledEventReceiptPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ReminderScheduledEventReceiptPersistenceAdapter(repository);
    }

    @Test
    void saveShouldReturnFalseForDuplicateKeyViolation() throws SQLException {
        doThrow(new DataIntegrityViolationException(
                "duplicate key",
                new SQLException("duplicate key", "23505")
        )).when(repository).save(any(ReminderScheduledEventReceiptJpaEntity.class));

        boolean saved = adapter.save(receipt());

        assertFalse(saved);
        verify(repository).save(any(ReminderScheduledEventReceiptJpaEntity.class));
    }

    @Test
    void saveShouldRethrowNonDuplicateIntegrityViolation() throws SQLException {
        doThrow(new DataIntegrityViolationException(
                "foreign key violation",
                new SQLException("foreign key violation", "23503")
        )).when(repository).save(any(ReminderScheduledEventReceiptJpaEntity.class));

        assertThrows(DataIntegrityViolationException.class, () -> adapter.save(receipt()));
    }

    @Test
    void saveShouldReturnTrueWhenRepositoryAcceptsReceipt() {
        assertTrue(adapter.save(receipt()));
        verify(repository).save(any(ReminderScheduledEventReceiptJpaEntity.class));
    }

    private ReminderScheduledEventReceipt receipt() {
        return new ReminderScheduledEventReceipt(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "todo.reminder.scheduled.v1",
                "v1",
                Instant.parse("2026-04-21T10:00:00Z"),
                Instant.parse("2026-04-21T10:00:05Z"),
                0,
                42L,
                "{\"eventId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}"
        );
    }
}
