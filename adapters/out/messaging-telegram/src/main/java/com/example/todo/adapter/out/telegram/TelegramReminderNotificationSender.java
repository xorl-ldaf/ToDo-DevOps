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
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final MeterRegistry meterRegistry;

    public TelegramReminderNotificationSender(
            RestClient restClient,
            String botToken,
            int maxAttempts,
            Duration retryBackoff,
            MeterRegistry meterRegistry
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.botToken = requireText(botToken, "botToken");
        this.maxAttempts = requirePositive(maxAttempts, "maxAttempts");
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public ReminderNotificationDeliveryResult deliver(ReminderNotificationV1 notification) {
        ReminderNotificationV1 actualNotification = Objects.requireNonNull(
                notification,
                "notification must not be null"
        );

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
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
                    recordAttempt(sample, "retryable_failure", attempt);
                    if (attempt < maxAttempts) {
                        if (!scheduleRetry(actualNotification, result.reason(), attempt)) {
                            return ReminderNotificationDeliveryResult.retryableFailure(
                                    "telegram retry interrupted during backoff"
                            );
                        }
                        continue;
                    }
                    log.warn(
                            "Telegram delivery exhausted retries reminderId={} attempt={} reason={}",
                            actualNotification.reminderId(),
                            attempt,
                            result.reason()
                    );
                    return result;
                }
                if (!response.ok()) {
                    ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.permanentFailure(
                            "telegram API rejected message: " + response.description()
                    );
                    recordAttempt(sample, "permanent_failure", attempt);
                    log.warn(
                            "Telegram delivery failed permanently reminderId={} attempt={} reason={}",
                            actualNotification.reminderId(),
                            attempt,
                            result.reason()
                    );
                    return result;
                }

                recordAttempt(sample, "delivered", attempt);
                return ReminderNotificationDeliveryResult.delivered();
            } catch (RestClientResponseException exception) {
                ReminderNotificationDeliveryResult result = classifyHttpFailure(exception);
                recordAttempt(sample, tagValue(result), attempt);
                if (result.retryableFailure() && attempt < maxAttempts) {
                    if (!scheduleRetry(actualNotification, result.reason(), attempt)) {
                        return ReminderNotificationDeliveryResult.retryableFailure(
                                "telegram retry interrupted during backoff"
                        );
                    }
                    continue;
                }
                logFailure(actualNotification, attempt, result, exception);
                return result;
            } catch (ResourceAccessException exception) {
                ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.retryableFailure(
                        "telegram transport error: " + exception.getClass().getSimpleName()
                );
                recordAttempt(sample, "retryable_failure", attempt);
                if (attempt < maxAttempts) {
                    if (!scheduleRetry(actualNotification, result.reason(), attempt)) {
                        return ReminderNotificationDeliveryResult.retryableFailure(
                                "telegram retry interrupted during backoff"
                        );
                    }
                    continue;
                }
                logFailure(actualNotification, attempt, result, exception);
                return result;
            } catch (RestClientException exception) {
                ReminderNotificationDeliveryResult result = ReminderNotificationDeliveryResult.retryableFailure(
                        "telegram client error: " + exception.getClass().getSimpleName()
                );
                recordAttempt(sample, "retryable_failure", attempt);
                if (attempt < maxAttempts) {
                    if (!scheduleRetry(actualNotification, result.reason(), attempt)) {
                        return ReminderNotificationDeliveryResult.retryableFailure(
                                "telegram retry interrupted during backoff"
                        );
                    }
                    continue;
                }
                logFailure(actualNotification, attempt, result, exception);
                return result;
            }
        }

        return ReminderNotificationDeliveryResult.retryableFailure("telegram delivery exhausted retries");
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

    private ReminderNotificationDeliveryResult classifyHttpFailure(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        if (statusCode == 429 || statusCode >= 500) {
            return ReminderNotificationDeliveryResult.retryableFailure("telegram HTTP " + statusCode);
        }
        return ReminderNotificationDeliveryResult.permanentFailure("telegram HTTP " + statusCode);
    }

    private boolean scheduleRetry(ReminderNotificationV1 notification, String reason, int attempt) {
        meterRegistry.counter(
                "todo.reminder.delivery.retries",
                "channel", "telegram"
        ).increment();
        log.warn(
                "Retrying Telegram delivery reminderId={} attempt={} maxAttempts={} backoffMs={} reason={}",
                notification.reminderId(),
                attempt,
                maxAttempts,
                retryBackoff.toMillis(),
                reason
        );
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Interrupted while backing off Telegram delivery reminderId={} attempt={}",
                    notification.reminderId(),
                    attempt,
                    exception
            );
            return false;
        }
        return true;
    }

    private void logFailure(
            ReminderNotificationV1 notification,
            int attempt,
            ReminderNotificationDeliveryResult result,
            Exception exception
    ) {
        if (result.permanentFailure()) {
            log.warn(
                    "Telegram delivery failed permanently reminderId={} attempt={} reason={}",
                    notification.reminderId(),
                    attempt,
                    result.reason(),
                    exception
            );
            return;
        }

        log.error(
                "Telegram delivery failed after retries reminderId={} attempt={} maxAttempts={} reason={}",
                notification.reminderId(),
                attempt,
                maxAttempts,
                result.reason(),
                exception
        );
    }

    private void recordAttempt(Timer.Sample sample, String outcome, int attempt) {
        sample.stop(meterRegistry.timer(
                "todo.reminder.delivery.attempt.duration",
                "channel", "telegram",
                "outcome", outcome
        ));
        meterRegistry.counter(
                "todo.reminder.delivery.attempts",
                "channel", "telegram",
                "outcome", outcome,
                "attempt", Integer.toString(attempt)
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
