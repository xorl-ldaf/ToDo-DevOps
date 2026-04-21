package com.example.todo.config;

import com.example.todo.adapter.out.telegram.TelegramReminderNotificationSender;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(TodoTelegramProperties.class)
public class TelegramConfig {

    @Bean
    @ConditionalOnProperty(prefix = "todo.telegram", name = "enabled", havingValue = "true")
    DeliverReminderNotificationPort telegramReminderNotificationSender(
            TodoTelegramProperties properties,
            MeterRegistry meterRegistry
    ) {
        String botToken = properties.requireBotToken();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.requireConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.requireReadTimeout().toMillis());
        return new TelegramReminderNotificationSender(
                org.springframework.web.client.RestClient.builder()
                        .baseUrl(properties.getBaseUrl())
                        .requestFactory(requestFactory)
                        .build(),
                botToken,
                properties.requireMaxAttempts(),
                properties.requireRetryBackoff(),
                meterRegistry
        );
    }

    @Bean
    @ConditionalOnMissingBean(DeliverReminderNotificationPort.class)
    DeliverReminderNotificationPort noOpReminderNotificationSender() {
        return new NoOpReminderNotificationSender();
    }
}
