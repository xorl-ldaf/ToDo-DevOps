package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderDeliveryPolicyTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    private final ReminderDeliveryPolicy policy = new ReminderDeliveryPolicy();

    @Test
    void shouldRetryRetryableFailureWhenAttemptsRemain() {
        assertTrue(policy.shouldRetry(
                ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"),
                1,
                3
        ));
    }

    @Test
    void shouldRetryShouldRejectNonRetryableFailure() {
        assertFalse(policy.shouldRetry(
                ReminderNotificationDeliveryResult.permanentFailure("chat forbidden"),
                0,
                3
        ));
    }

    @Test
    void shouldRetryShouldRejectRetryableFailureWhenBudgetIsExhausted() {
        assertFalse(policy.shouldRetry(
                ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"),
                2,
                3
        ));
    }

    @Test
    void shouldRetryShouldRejectMissingDeliveryResult() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.shouldRetry(null, 0, 3)
        );

        assertEquals("deliveryResult must not be null", exception.getMessage());
    }

    @Test
    void shouldRetryShouldRejectInvalidDeliveryAttempts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.shouldRetry(
                        ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"),
                        -1,
                        3
                )
        );

        assertEquals("deliveryAttempts must not be negative", exception.getMessage());
    }

    @Test
    void shouldRetryShouldRejectInvalidMaxAttempts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.shouldRetry(
                        ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"),
                        0,
                        0
                )
        );

        assertEquals("maxDeliveryAttempts must be at least 1", exception.getMessage());
    }

    @Test
    void shouldRetryWithReminderShouldUseReminderDeliveryAttempts() {
        assertTrue(policy.shouldRetry(
                reminderWithAttempts(1),
                ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"),
                3
        ));
    }

    @Test
    void shouldRetryWithReminderShouldRejectMissingReminder() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.shouldRetry(
                        (Reminder) null,
                        ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"),
                        3
                )
        );

        assertEquals("reminder must not be null", exception.getMessage());
    }

    private Reminder reminderWithAttempts(int deliveryAttempts) {
        return Reminder.restore(
                new ReminderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                TaskId.newId(),
                NOW.minusSeconds(60),
                ReminderStatus.PROCESSING,
                NOW.minusSeconds(600),
                NOW.minusSeconds(30),
                NOW.minusSeconds(60),
                NOW.minusSeconds(1),
                "worker-a",
                null,
                deliveryAttempts,
                null
        );
    }
}
