package com.example.todo.config;

import com.example.todo.application.port.in.FlushReminderScheduledEventOutboxUseCase;
import com.example.todo.application.port.in.ReminderScheduledEventOutboxReport;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.Objects;

public final class ReminderScheduledEventOutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduledEventOutboxScheduler.class);

    private final FlushReminderScheduledEventOutboxUseCase flushReminderScheduledEventOutboxUseCase;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ReminderScheduledEventOutboxScheduler(
            FlushReminderScheduledEventOutboxUseCase flushReminderScheduledEventOutboxUseCase,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.flushReminderScheduledEventOutboxUseCase = Objects.requireNonNull(
                flushReminderScheduledEventOutboxUseCase,
                "flushReminderScheduledEventOutboxUseCase must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Scheduled(
            initialDelayString = "${todo.kafka.outbox.initial-delay}",
            fixedDelayString = "${todo.kafka.outbox.fixed-delay}"
    )
    public void flush() {
        try {
            ReminderScheduledEventOutboxReport report = flushReminderScheduledEventOutboxUseCase.flush(clock.instant());
            meterRegistry.counter("todo.kafka.outbox.scans", "outcome", "success").increment();
            meterRegistry.counter("todo.kafka.outbox.claimed").increment(report.claimedCount());
            meterRegistry.counter("todo.kafka.outbox.results", "outcome", "published").increment(report.publishedCount());
            meterRegistry.counter("todo.kafka.outbox.results", "outcome", "retried").increment(report.retriedCount());
            meterRegistry.counter("todo.kafka.outbox.results", "outcome", "failed").increment(report.failedCount());
            meterRegistry.counter("todo.kafka.outbox.results", "outcome", "conflict").increment(report.concurrencyConflictCount());
            if (report.claimedCount() > 0) {
                log.info(
                        "Flushed reminder outbox claimed={} published={} retried={} failed={} conflicts={}",
                        report.claimedCount(),
                        report.publishedCount(),
                        report.retriedCount(),
                        report.failedCount(),
                        report.concurrencyConflictCount()
                );
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter("todo.kafka.outbox.scans", "outcome", "failure").increment();
            log.error("Reminder scheduled event outbox flush failed", exception);
        }
    }
}
