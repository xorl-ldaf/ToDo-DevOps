package com.example.todo.adapter.out.telegram;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class TelegramReminderNotificationSender implements DeliverReminderNotificationPort {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final Logger log = LoggerFactory.getLogger(TelegramReminderNotificationSender.class);

    private final RestClient restClient;
    private final String botToken;
    private final MeterRegistry meterRegistry;

    public TelegramReminderNotificationSender(
            RestClient restClient,
            String botToken,
            MeterRegistry meterRegistry
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.botToken = requireText(botToken, "botToken");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public ReminderNotificationDeliveryResult deliver(ReminderNotificationV1 notification) {
        ReminderNotificationV1 actualNotification = Objects.requireNonNull(
                notification,
                "notification must not be null"
        );

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            TelegramApiResponse response = restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TelegramSendMessageRequest(
                            actualNotification.recipientTelegramChatId(),
                            formatMessage(actualNotification)
                    ))
                    .retrieve()
                    .body(TelegramApiResponse.class);

            if (response == null) {
                ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.retryableFailure(
                        "telegram API returned an empty response"
                );
                recordAttempt(sample, "retryable_failure");
                logFailure(actualNotification, result, null);
                return result;
            }
            if (!response.ok()) {
                ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.permanentFailure(
                        "telegram API rejected message: " + response.description()
                );
                recordAttempt(sample, "permanent_failure");
                logFailure(actualNotification, result, null);
                return result;
            }

            recordAttempt(sample, "delivered");
            return ReminderNotificationDeliveryResult.delivered();
        } catch (RestClientResponseException exception) {
            ReminderNotificationDeliveryResult result = classifyHttpFailure(exception);
            recordAttempt(sample, tagValue(result));
            logFailure(actualNotification, result, exception);
            return result;
        } catch (ResourceAccessException exception) {
            ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.retryableFailure(
                    "telegram transport error: " + exception.getClass().getSimpleName()
            );
            recordAttempt(sample, "retryable_failure");
            logFailure(actualNotification, result, exception);
            return result;
        } catch (RestClientException exception) {
            ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.retryableFailure(
                    "telegram client error: " + exception.getClass().getSimpleName()
            );
            recordAttempt(sample, "retryable_failure");
            logFailure(actualNotification, result, exception);
            return result;
        }
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

    private ReminderNotificationDeliveryResult classifyHttpFailure(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        if (statusCode == 429 || statusCode >= 500) {
            return ReminderNotificationDeliveryResult.retryableFailure("telegram HTTP " + statusCode);
        }
        return ReminderNotificationDeliveryResult.permanentFailure("telegram HTTP " + statusCode);
    }

    private void logFailure(
            ReminderNotificationV1 notification,
            ReminderNotificationDeliveryResult result,
            Exception exception
    ) {
        if (result.permanentFailure()) {
            log.warn(
                    "Telegram delivery failed permanently reminderId={} reason={}",
                    notification.reminderId(),
                    result.reason(),
                    exception
            );
            return;
        }

        log.error(
                "Telegram delivery failed reminderId={} reason={}",
                notification.reminderId(),
                result.reason(),
                exception
        );
    }

    private void recordAttempt(Timer.Sample sample, String outcome) {
        sample.stop(meterRegistry.timer(
                "todo.reminder.delivery.attempt.duration",
                "channel", "telegram",
                "outcome", outcome
        ));
        meterRegistry.counter(
                "todo.reminder.delivery.attempts",
                "channel", "telegram",
                "outcome", outcome
        ).increment();
    }

    private String tagValue(ReminderNotificationDeliveryResult result) {
        if (result.deliveredSuccessfully()) {
            return "delivered";
        }
        if (result.permanentFailure()) {
            return "permanent_failure";
        }
        if (result.retryableFailure()) {
            return "retryable_failure";
        }
        return "skipped";
    }
}
