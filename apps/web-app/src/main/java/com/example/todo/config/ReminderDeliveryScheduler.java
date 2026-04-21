package com.example.todo.config;

import com.example.todo.application.port.in.ScanDueRemindersUseCase;
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
            int deliveredCount = scanDueRemindersUseCase.scanAndPublishDueReminders(clock.instant());
            meterRegistry.counter("todo.reminder.delivery.scans", "outcome", "success").increment();
            meterRegistry.counter("todo.reminder.delivery.delivered", "channel", "telegram").increment(deliveredCount);
            if (deliveredCount > 0) {
                log.info("Delivered {} due reminder notification(s) via outbound adapters", deliveredCount);
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter("todo.reminder.delivery.scans", "outcome", "failure").increment();
            log.error("Reminder delivery scan failed", exception);
        }
    }
}
