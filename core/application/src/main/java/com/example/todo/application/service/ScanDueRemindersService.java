package com.example.todo.application.service;

import com.example.todo.application.factory.ReminderNotificationFactory;
import com.example.todo.application.policy.ReminderDeliveryPolicy;
import com.example.todo.application.policy.ReminderFailureReasonPolicy;
import com.example.todo.application.policy.ReminderLifecyclePolicy;
import com.example.todo.application.port.in.ReminderProcessingReport;
import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ScanDueRemindersService implements ScanDueRemindersUseCase {
    private final ClaimDueRemindersPort claimDueRemindersPort;
    private final LoadTaskPort loadTaskPort;
    private final LoadUserDetailsPort loadUserDetailsPort;
    private final DeliverReminderNotificationPort deliverReminderNotificationPort;
    private final FinalizeReminderDeliveryPort finalizeReminderDeliveryPort;
    private final String processorId;
    private final int batchSize;
    private final int maxDeliveryAttempts;
    private final Duration retryBackoff;
    private final Duration processingTimeout;
    private final ReminderDeliveryPolicy reminderDeliveryPolicy;
    private final ReminderLifecyclePolicy reminderLifecyclePolicy;
    private final ReminderNotificationFactory reminderNotificationFactory = new ReminderNotificationFactory();
    private final ReminderFailureReasonPolicy reminderFailureReasonPolicy = new ReminderFailureReasonPolicy();

    public ScanDueRemindersService(
            ClaimDueRemindersPort claimDueRemindersPort,
            LoadTaskPort loadTaskPort,
            LoadUserDetailsPort loadUserDetailsPort,
            DeliverReminderNotificationPort deliverReminderNotificationPort,
            FinalizeReminderDeliveryPort finalizeReminderDeliveryPort,
            String processorId,
            int batchSize,
            int maxDeliveryAttempts,
            Duration retryBackoff,
            Duration processingTimeout
    ) {
        this(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort,
                processorId,
                batchSize,
                maxDeliveryAttempts,
                retryBackoff,
                processingTimeout,
                new ReminderDeliveryPolicy(),
                new ReminderLifecyclePolicy()
        );
    }

    public ScanDueRemindersService(
            ClaimDueRemindersPort claimDueRemindersPort,
            LoadTaskPort loadTaskPort,
            LoadUserDetailsPort loadUserDetailsPort,
            DeliverReminderNotificationPort deliverReminderNotificationPort,
            FinalizeReminderDeliveryPort finalizeReminderDeliveryPort,
            String processorId,
            int batchSize,
            int maxDeliveryAttempts,
            Duration retryBackoff,
            Duration processingTimeout,
            ReminderDeliveryPolicy reminderDeliveryPolicy
    ) {
        this(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort,
                processorId,
                batchSize,
                maxDeliveryAttempts,
                retryBackoff,
                processingTimeout,
                reminderDeliveryPolicy,
                new ReminderLifecyclePolicy()
        );
    }

    public ScanDueRemindersService(
            ClaimDueRemindersPort claimDueRemindersPort,
            LoadTaskPort loadTaskPort,
            LoadUserDetailsPort loadUserDetailsPort,
            DeliverReminderNotificationPort deliverReminderNotificationPort,
            FinalizeReminderDeliveryPort finalizeReminderDeliveryPort,
            String processorId,
            int batchSize,
            int maxDeliveryAttempts,
            Duration retryBackoff,
            Duration processingTimeout,
            ReminderDeliveryPolicy reminderDeliveryPolicy,
            ReminderLifecyclePolicy reminderLifecyclePolicy
    ) {
        this.claimDueRemindersPort = Objects.requireNonNull(claimDueRemindersPort, "claimDueRemindersPort must not be null");
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
        this.loadUserDetailsPort = Objects.requireNonNull(loadUserDetailsPort, "loadUserDetailsPort must not be null");
        this.deliverReminderNotificationPort = Objects.requireNonNull(
                deliverReminderNotificationPort,
                "deliverReminderNotificationPort must not be null"
        );
        this.finalizeReminderDeliveryPort = Objects.requireNonNull(
                finalizeReminderDeliveryPort,
                "finalizeReminderDeliveryPort must not be null"
        );
        this.processorId = requireText(processorId, "processorId");
        this.batchSize = requirePositive(batchSize, "batchSize");
        this.maxDeliveryAttempts = requirePositive(maxDeliveryAttempts, "maxDeliveryAttempts");
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        this.processingTimeout = requirePositive(processingTimeout, "processingTimeout");
        this.reminderDeliveryPolicy = Objects.requireNonNull(
                reminderDeliveryPolicy,
                "reminderDeliveryPolicy must not be null"
        );
        this.reminderLifecyclePolicy = Objects.requireNonNull(
                reminderLifecyclePolicy,
                "reminderLifecyclePolicy must not be null"
        );
    }

    @Override
    public ReminderProcessingReport processDueReminders(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        List<Reminder> reminders = claimDueRemindersPort.claimDueReminders(
                now,
                processingTimeout,
                batchSize,
                reminder -> reminderLifecyclePolicy.markProcessing(reminder, processorId, now)
        );
        if (reminders.isEmpty()) {
            return ReminderProcessingReport.empty();
        }

        int deliveredCount = 0;
        int retriedCount = 0;
        int failedCount = 0;
        int concurrencyConflictCount = 0;

        for (Reminder reminder : reminders) {
            ReminderProcessingOutcome outcome = processReminder(reminder, now);
            switch (outcome) {
                case DELIVERED -> deliveredCount++;
                case RETRIED -> retriedCount++;
                case FAILED -> failedCount++;
                case CONCURRENCY_CONFLICT -> concurrencyConflictCount++;
            }
        }

        return new ReminderProcessingReport(
                reminders.size(),
                deliveredCount,
                retriedCount,
                failedCount,
                concurrencyConflictCount
        );
    }

    private ReminderProcessingOutcome processReminder(Reminder reminder, Instant now) {
        Task task = loadTaskPort.loadById(reminder.getTaskId()).orElse(null);
        if (task == null) {
            return markFailed(reminder, now, reminderFailureReasonPolicy.taskMissing());
        }

        User recipient = loadUserDetailsPort.loadById(task.getAssigneeId()).orElse(null);
        if (recipient == null) {
            return markFailed(reminder, now, reminderFailureReasonPolicy.assigneeMissing());
        }
        if (reminderFailureReasonPolicy.hasNoTelegramChatId(recipient)) {
            return markFailed(reminder, now, reminderFailureReasonPolicy.noTelegramChatId());
        }

        ReminderNotificationDeliveryResult deliveryResult = deliverReminderNotificationPort.deliver(
                reminderNotificationFactory.create(reminder, task, recipient, now)
        );

        return finalizeDelivery(reminder, now, deliveryResult);
    }

    private ReminderProcessingOutcome finalizeDelivery(
            Reminder reminder,
            Instant now,
            ReminderNotificationDeliveryResult deliveryResult
    ) {
        if (deliveryResult.deliveredSuccessfully()) {
            return markDelivered(reminder, now);
        }

        if (reminderDeliveryPolicy.shouldRetry(
                deliveryResult,
                reminder.getDeliveryAttempts(),
                maxDeliveryAttempts
        )) {
            return reschedule(reminder, now, deliveryResult.reason());
        }

        return markFailed(reminder, now, deliveryResult.reason());
    }

    private ReminderProcessingOutcome markDelivered(Reminder reminder, Instant now) {
        Reminder deliveredReminder = reminderLifecyclePolicy.markDelivered(reminder, now);
        if (!finalizeReminderDeliveryPort.finalizeDelivery(deliveredReminder, processorId)) {
            return ReminderProcessingOutcome.CONCURRENCY_CONFLICT;
        }
        return ReminderProcessingOutcome.DELIVERED;
    }

    private ReminderProcessingOutcome reschedule(Reminder reminder, Instant now, String failureReason) {
        Instant nextAttemptAt = now.plus(retryBackoff);
        Reminder rescheduledReminder = reminderLifecyclePolicy.reschedule(reminder, now, nextAttemptAt, failureReason);
        if (!finalizeReminderDeliveryPort.finalizeDelivery(rescheduledReminder, processorId)) {
            return ReminderProcessingOutcome.CONCURRENCY_CONFLICT;
        }
        return ReminderProcessingOutcome.RETRIED;
    }

    private ReminderProcessingOutcome markFailed(Reminder reminder, Instant now, String failureReason) {
        Reminder failedReminder = reminderLifecyclePolicy.markFailed(reminder, now, failureReason);
        if (!finalizeReminderDeliveryPort.finalizeDelivery(failedReminder, processorId)) {
            return ReminderProcessingOutcome.CONCURRENCY_CONFLICT;
        }
        return ReminderProcessingOutcome.FAILED;
    }

    private static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Duration actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isNegative() || actualValue.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return actualValue;
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }

    private enum ReminderProcessingOutcome {
        DELIVERED,
        RETRIED,
        FAILED,
        CONCURRENCY_CONFLICT
    }
}
