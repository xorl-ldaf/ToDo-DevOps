package com.example.todo.config;

import com.example.todo.adapter.out.telegram.TelegramReminderNotificationSender;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TodoTelegramProperties.class)
public class TelegramConfig {

    @Bean
    @ConditionalOnProperty(prefix = "todo.telegram", name = "enabled", havingValue = "true")
    DeliverReminderNotificationPort telegramReminderNotificationSender(
            TodoTelegramProperties properties
    ) {
        String botToken = properties.requireBotToken();
        return new TelegramReminderNotificationSender(
                org.springframework.web.client.RestClient.builder()
                        .baseUrl(properties.getBaseUrl())
                        .build(),
                botToken
        );
    }

    @Bean
    @ConditionalOnMissingBean(DeliverReminderNotificationPort.class)
    DeliverReminderNotificationPort noOpReminderNotificationSender() {
        return new NoOpReminderNotificationSender();
    }
}
