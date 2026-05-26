package com.example.todo.config;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.outbox.ReminderScheduledEventOutboxMessage;
import com.example.todo.application.port.in.ReminderProcessingReport;
import com.example.todo.application.port.in.ReminderScheduledEventOutboxReport;
import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.ClaimReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.application.port.out.FinalizeReminderScheduledEventOutboxPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.application.service.FlushReminderScheduledEventOutboxService;
import com.example.todo.application.service.ScanDueRemindersService;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionalWorkerPortDecoratorTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void reminderDeliveryDecoratorWrapsClaimAndFinalizeInShortTransactions() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        AtomicInteger activeDelegateCalls = new AtomicInteger();

        ClaimDueRemindersPort claimPort = (now, processingTimeout, limit, claimTransition) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            activeDelegateCalls.incrementAndGet();
            return List.of();
        };
        FinalizeReminderDeliveryPort finalizePort = (reminder, processorId) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            activeDelegateCalls.incrementAndGet();
            return true;
        };

        TransactionalReminderDeliveryPersistencePorts decorator =
                new TransactionalReminderDeliveryPersistencePorts(claimPort, finalizePort, transactionManager);

        decorator.claimDueReminders(Instant.parse("2026-04-21T10:00:00Z"), Duration.ofSeconds(30), 10, reminder -> reminder);
        assertTrue(decorator.finalizeDelivery(null, "processor-1"));

        assertEquals(2, activeDelegateCalls.get());
        assertEquals(2, transactionManager.commits());
    }

    @Test
    void outboxDecoratorWrapsClaimAndFinalizeInShortTransactions() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        AtomicInteger activeDelegateCalls = new AtomicInteger();
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant now = Instant.parse("2026-04-21T10:00:00Z");

        ClaimReminderScheduledEventOutboxPort claimPort = (claimNow, processorId, processingTimeout, limit) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            activeDelegateCalls.incrementAndGet();
            return List.of();
        };
        FinalizeReminderScheduledEventOutboxPort finalizePort = new FinalizeReminderScheduledEventOutboxPort() {
            @Override
            public boolean markPublished(UUID eventId, String processorId, Instant publishedAt) {
                assertActiveTransaction();
                return true;
            }

            @Override
            public boolean reschedule(
                    UUID eventId,
                    String processorId,
                    Instant processedAt,
                    Instant nextAttemptAt,
                    String failureReason
            ) {
                assertActiveTransaction();
                return true;
            }

            @Override
            public boolean markFailed(UUID eventId, String processorId, Instant processedAt, String failureReason) {
                assertActiveTransaction();
                return true;
            }

            private void assertActiveTransaction() {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                activeDelegateCalls.incrementAndGet();
            }
        };

        TransactionalReminderScheduledEventOutboxPorts decorator =
                new TransactionalReminderScheduledEventOutboxPorts(claimPort, finalizePort, transactionManager);

        decorator.claimPending(now, "processor-1", Duration.ofSeconds(30), 10);
        assertTrue(decorator.markPublished(eventId, "processor-1", now));
        assertTrue(decorator.reschedule(eventId, "processor-1", now, now.plusSeconds(5), "retry"));
        assertTrue(decorator.markFailed(eventId, "processor-1", now, "failed"));

        assertEquals(4, activeDelegateCalls.get());
        assertEquals(4, transactionManager.commits());
    }

    @Test
    void reminderDeliveryRunsBetweenShortClaimAndFinalizeTransactions() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        Instant now = Instant.parse("2026-04-21T10:00:00Z");
        Reminder scheduledReminder = scheduledReminder(now);
        UserId userId = new UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        Task task = new Task(
                scheduledReminder.getTaskId(),
                userId,
                userId,
                "Send update",
                "Check transaction boundaries",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                now,
                now
        );
        User user = new User(userId, "worker.user", "Worker User", new TelegramChatId(123456789L), now, now);
        AtomicInteger deliveryCalls = new AtomicInteger();

        ClaimDueRemindersPort claimPort = (claimNow, processingTimeout, limit, claimTransition) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(claimTransition.apply(scheduledReminder));
        };
        FinalizeReminderDeliveryPort finalizePort = (reminder, processorId) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(ReminderStatus.DELIVERED, reminder.getStatus());
            return true;
        };
        TransactionalReminderDeliveryPersistencePorts transactionalPorts =
                new TransactionalReminderDeliveryPersistencePorts(claimPort, finalizePort, transactionManager);

        ScanDueRemindersService service = new ScanDueRemindersService(
                transactionalPorts,
                taskId -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    return Optional.of(task);
                },
                recipientId -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    return Optional.of(user);
                },
                notification -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    deliveryCalls.incrementAndGet();
                    return ReminderNotificationDeliveryResult.delivered();
                },
                transactionalPorts,
                "processor-1",
                1,
                3,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        ReminderProcessingReport report = service.processDueReminders(now);

        assertEquals(new ReminderProcessingReport(1, 1, 0, 0, 0), report);
        assertEquals(1, deliveryCalls.get());
        assertEquals(2, transactionManager.commits());
    }

    @Test
    void outboxPublicationRunsBetweenShortClaimAndFinalizeTransactions() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        Instant now = Instant.parse("2026-04-21T10:00:00Z");
        ReminderScheduledEventOutboxMessage message = outboxMessage(now);
        AtomicInteger publishCalls = new AtomicInteger();

        ClaimReminderScheduledEventOutboxPort claimPort = (claimNow, processorId, processingTimeout, limit) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(message);
        };
        FinalizeReminderScheduledEventOutboxPort finalizePort = new FinalizeReminderScheduledEventOutboxPort() {
            @Override
            public boolean markPublished(UUID eventId, String processorId, Instant publishedAt) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return true;
            }

            @Override
            public boolean reschedule(
                    UUID eventId,
                    String processorId,
                    Instant processedAt,
                    Instant nextAttemptAt,
                    String failureReason
            ) {
                throw new AssertionError("publish should succeed");
            }

            @Override
            public boolean markFailed(UUID eventId, String processorId, Instant processedAt, String failureReason) {
                throw new AssertionError("publish should succeed");
            }
        };
        TransactionalReminderScheduledEventOutboxPorts transactionalPorts =
                new TransactionalReminderScheduledEventOutboxPorts(claimPort, finalizePort, transactionManager);
        FlushReminderScheduledEventOutboxService service = new FlushReminderScheduledEventOutboxService(
                transactionalPorts,
                transactionalPorts,
                event -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    publishCalls.incrementAndGet();
                },
                "processor-1",
                1,
                3,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        ReminderScheduledEventOutboxReport report = service.flush(now);

        assertEquals(new ReminderScheduledEventOutboxReport(1, 1, 0, 0, 0), report);
        assertEquals(1, publishCalls.get());
        assertEquals(2, transactionManager.commits());
    }

    private Reminder scheduledReminder(Instant now) {
        return Reminder.restore(
                new ReminderId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new TaskId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                now,
                ReminderStatus.SCHEDULED,
                now,
                now,
                now,
                null,
                null,
                null,
                0,
                null
        );
    }

    private ReminderScheduledEventOutboxMessage outboxMessage(Instant now) {
        ReminderScheduledEventV1 event = new ReminderScheduledEventV1(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                ReminderScheduledEventV1.EVENT_TYPE,
                ReminderScheduledEventV1.EVENT_VERSION,
                now,
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                now.plusSeconds(300),
                "SCHEDULED"
        );
        return new ReminderScheduledEventOutboxMessage(event.eventId(), event, 0, now);
    }

    private static final class TrackingTransactionManager implements PlatformTransactionManager {
        private final AtomicInteger commits = new AtomicInteger();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            commits.incrementAndGet();
        }

        @Override
        public void rollback(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        private int commits() {
            return commits.get();
        }
    }
}
