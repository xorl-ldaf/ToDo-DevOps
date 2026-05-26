package com.example.todo.adapter.out.telegram;

import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

final class TelegramDeliveryFailureClassifier {
    static final String EMPTY_RESPONSE_REASON = "telegram.api.empty_response";
    static final String API_REJECTION_REASON = "telegram.api.rejected";
    static final String TRANSPORT_ERROR_REASON = "telegram.transport_error";
    static final String CLIENT_ERROR_REASON = "telegram.client_error";

    private TelegramDeliveryFailureClassifier() {
    }

    static ReminderNotificationDeliveryResult emptyResponse() {
        return ReminderNotificationDeliveryResult.retryableFailure(EMPTY_RESPONSE_REASON);
    }

    static ReminderNotificationDeliveryResult apiRejectedMessage() {
        return ReminderNotificationDeliveryResult.permanentFailure(API_REJECTION_REASON);
    }

    static ReminderNotificationDeliveryResult httpFailure(int statusCode) {
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return ReminderNotificationDeliveryResult.retryableFailure(reason("telegram.http.retryable", statusCode));
        }
        if (statusCode == 400) {
            return ReminderNotificationDeliveryResult.permanentFailure(
                    reason("telegram.http.invalid_request", statusCode)
            );
        }
        if (statusCode == 403) {
            return ReminderNotificationDeliveryResult.permanentFailure(reason("telegram.http.forbidden", statusCode));
        }
        return ReminderNotificationDeliveryResult.permanentFailure(reason("telegram.http.non_retryable", statusCode));
    }

    static ReminderNotificationDeliveryResult transportFailure() {
        return ReminderNotificationDeliveryResult.retryableFailure(TRANSPORT_ERROR_REASON);
    }

    static ReminderNotificationDeliveryResult clientFailure(RestClientException exception) {
        if (hasCause(exception, IOException.class)) {
            return transportFailure();
        }
        return ReminderNotificationDeliveryResult.retryableFailure(CLIENT_ERROR_REASON);
    }

    private static String reason(String code, int statusCode) {
        return code + " status=" + statusCode;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
