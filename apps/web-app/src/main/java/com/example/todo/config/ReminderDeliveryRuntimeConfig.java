package com.example.todo.config;

import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class ReminderDeliveryRuntimeConfig {

    @Bean
    @ConditionalOnProperty(prefix = "todo.reminder-delivery", name = "enabled", havingValue = "true")
    ReminderDeliveryScheduler reminderDeliveryScheduler(
            ScanDueRemindersUseCase scanDueRemindersUseCase,
            Clock clock
    ) {
        return new ReminderDeliveryScheduler(scanDueRemindersUseCase, clock);
    }
}
