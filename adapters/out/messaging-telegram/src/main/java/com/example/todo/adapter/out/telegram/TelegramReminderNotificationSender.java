package com.example.todo.adapter.out.telegram;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class TelegramReminderNotificationSender implements DeliverReminderNotificationPort {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final RestClient restClient;
    private final String botToken;

    public TelegramReminderNotificationSender(RestClient restClient, String botToken) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.botToken = requireText(botToken, "botToken");
    }

    @Override
    public ReminderNotificationDeliveryResult deliver(ReminderNotificationV1 notification) {
        ReminderNotificationV1 actualNotification = Objects.requireNonNull(
                notification,
                "notification must not be null"
        );

        TelegramApiResponse response;
        try {
            response = restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TelegramSendMessageRequest(
                            actualNotification.recipientTelegramChatId(),
                            formatMessage(actualNotification)
                    ))
                    .retrieve()
                    .body(TelegramApiResponse.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Telegram delivery request failed", exception);
        }

        if (response == null) {
            throw new IllegalStateException("Telegram API returned an empty response");
        }
        if (!response.ok()) {
            throw new IllegalStateException("Telegram API rejected message: " + response.description());
        }

        return ReminderNotificationDeliveryResult.delivered();
    }

    private String formatMessage(ReminderNotificationV1 notification) {
        StringBuilder message = new StringBuilder()
                .append("Reminder: ")
                .append(notification.taskTitle())
                .append('\n')
                .append("Assignee: ")
                .append(notification.recipientDisplayName())
                .append('\n')
                .append("Remind at: ")
                .append(TIMESTAMP_FORMATTER.format(notification.remindAt()));

        if (!notification.taskDescription().isBlank()) {
            message.append('\n')
                    .append("Description: ")
                    .append(notification.taskDescription());
        }

        return message.toString();
    }

    private static String requireText(String value, String fieldName) {
        String actualValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (actualValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return actualValue;
    }
}
