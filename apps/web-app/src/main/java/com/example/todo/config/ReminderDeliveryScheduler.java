package com.example.todo.config;

import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import com.example.todo.application.port.in.ReminderProcessingReport;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.Objects;

public final class ReminderDeliveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryScheduler.class);

    private final ScanDueRemindersUseCase scanDueRemindersUseCase;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ReminderDeliveryScheduler(
            ScanDueRemindersUseCase scanDueRemindersUseCase,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.scanDueRemindersUseCase = Objects.requireNonNull(
                scanDueRemindersUseCase,
                "scanDueRemindersUseCase must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Scheduled(
            initialDelayString = "${todo.reminder-delivery.initial-delay-ms}",
            fixedDelayString = "${todo.reminder-delivery.fixed-delay-ms}"
    )
    public void deliverDueReminders() {
        try {
            ReminderProcessingReport report = scanDueRemindersUseCase.scanAndPublishDueReminders(clock.instant());
            meterRegistry.counter("todo.reminder.delivery.scans", "outcome", "success").increment();
            meterRegistry.counter("todo.reminder.delivery.claimed").increment(report.claimedCount());
            meterRegistry.counter("todo.reminder.delivery.results", "outcome", "delivered").increment(report.deliveredCount());
            meterRegistry.counter("todo.reminder.delivery.results", "outcome", "retried").increment(report.retriedCount());
            meterRegistry.counter("todo.reminder.delivery.results", "outcome", "failed").increment(report.failedCount());
            meterRegistry.counter("todo.reminder.delivery.results", "outcome", "conflict")
                    .increment(report.concurrencyConflictCount());
            if (report.claimedCount() > 0) {
                log.info(
                        "Processed due reminders claimed={} delivered={} retried={} failed={} conflicts={}",
                        report.claimedCount(),
                        report.deliveredCount(),
                        report.retriedCount(),
                        report.failedCount(),
                        report.concurrencyConflictCount()
                );
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter("todo.reminder.delivery.scans", "outcome", "failure").increment();
            log.error("Reminder delivery scan failed", exception);
        }
    }
}
