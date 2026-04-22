package com.example.todo.application.service;

import com.example.todo.application.notification.ReminderNotificationV1;
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
import java.util.UUID;

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
    }

    @Override
    public ReminderProcessingReport scanAndPublishDueReminders(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        List<Reminder> reminders = claimDueRemindersPort.claimDueReminders(now, processorId, processingTimeout, batchSize);
        if (reminders.isEmpty()) {
            return ReminderProcessingReport.empty();
        }

        int deliveredCount = 0;
        int retriedCount = 0;
        int failedCount = 0;
        int concurrencyConflictCount = 0;

        for (Reminder reminder : reminders) {
            Task task = loadTaskPort.loadById(reminder.getTaskId()).orElse(null);
            if (task == null) {
                if (!finalizeReminderDeliveryPort.markFailed(
                        reminder.getId(),
                        processorId,
                        now,
                        "task no longer exists"
                )) {
                    concurrencyConflictCount++;
                } else {
                    failedCount++;
                }
                continue;
            }

            User recipient = loadUserDetailsPort.loadById(task.getAssigneeId()).orElse(null);
            if (recipient == null) {
                if (!finalizeReminderDeliveryPort.markFailed(
                        reminder.getId(),
                        processorId,
                        now,
                        "assignee no longer exists"
                )) {
                    concurrencyConflictCount++;
                } else {
                    failedCount++;
                }
                continue;
            }
            if (recipient.getTelegramChatId() == null) {
                if (!finalizeReminderDeliveryPort.markFailed(
                        reminder.getId(),
                        processorId,
                        now,
                        "recipient has no telegram chat id"
                )) {
                    concurrencyConflictCount++;
                } else {
                    failedCount++;
                }
                continue;
            }

            ReminderNotificationDeliveryResult deliveryResult = deliverReminderNotificationPort.deliver(
                    new ReminderNotificationV1(
                            UUID.randomUUID(),
                            ReminderNotificationV1.NOTIFICATION_TYPE,
                            ReminderNotificationV1.NOTIFICATION_VERSION,
                            now,
                            reminder.getId().value(),
                            task.getId().value(),
                            task.getTitle(),
                            task.getDescription(),
                            reminder.getRemindAt(),
                            recipient.getId().value(),
                            recipient.getDisplayName(),
                            recipient.getTelegramChatId().value()
                    )
            );

            if (deliveryResult.deliveredSuccessfully()) {
                if (!finalizeReminderDeliveryPort.markDelivered(reminder.getId(), processorId, now)) {
                    concurrencyConflictCount++;
                } else {
                    deliveredCount++;
                }
                continue;
            }

            if (shouldRetry(reminder, deliveryResult)) {
                Instant nextAttemptAt = now.plus(retryBackoff);
                if (!finalizeReminderDeliveryPort.reschedule(
                        reminder.getId(),
                        processorId,
                        now,
                        nextAttemptAt,
                        deliveryResult.reason()
                )) {
                    concurrencyConflictCount++;
                } else {
                    retriedCount++;
                }
                continue;
            }

            if (!finalizeReminderDeliveryPort.markFailed(
                    reminder.getId(),
                    processorId,
                    now,
                    deliveryResult.reason()
            )) {
                concurrencyConflictCount++;
            } else {
                failedCount++;
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

    private boolean shouldRetry(Reminder reminder, ReminderNotificationDeliveryResult deliveryResult) {
        return deliveryResult.retryableFailure()
                && reminder.getDeliveryAttempts() + 1 < maxDeliveryAttempts;
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
}
