package com.example.todo.adapter.out.telegram;

import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramDeliveryFailureClassifierTest {

    @Test
    void httpFailureShouldClassifyTransientStatusesAsRetryable() {
        assertRetryable(408, "telegram.http.retryable status=408");
        assertRetryable(429, "telegram.http.retryable status=429");
        assertRetryable(500, "telegram.http.retryable status=500");
        assertRetryable(503, "telegram.http.retryable status=503");
    }

    @Test
    void httpFailureShouldClassifyClientStatusesAsPermanent() {
        assertPermanent(400, "telegram.http.invalid_request status=400");
        assertPermanent(403, "telegram.http.forbidden status=403");
        assertPermanent(404, "telegram.http.non_retryable status=404");
    }

    @Test
    void apiFailureShouldNormalizeReasons() {
        ReminderNotificationDeliveryResult emptyResponse = TelegramDeliveryFailureClassifier.emptyResponse();
        ReminderNotificationDeliveryResult rejectedMessage = TelegramDeliveryFailureClassifier.apiRejectedMessage();

        assertTrue(emptyResponse.retryableFailure());
        assertEquals("telegram.api.empty_response", emptyResponse.reason());
        assertTrue(rejectedMessage.permanentFailure());
        assertEquals("telegram.api.rejected", rejectedMessage.reason());
    }

    @Test
    void clientFailuresShouldBeRetryableWithStableReasons() {
        ReminderNotificationDeliveryResult transportFailure = TelegramDeliveryFailureClassifier.transportFailure();
        ReminderNotificationDeliveryResult clientFailure = TelegramDeliveryFailureClassifier.clientFailure(
                new RestClientException("message conversion failed")
        );

        assertTrue(transportFailure.retryableFailure());
        assertEquals("telegram.transport_error", transportFailure.reason());
        assertTrue(clientFailure.retryableFailure());
        assertEquals("telegram.client_error", clientFailure.reason());
    }

    @Test
    void wrappedIoFailuresShouldBeClassifiedAsTransportFailures() {
        ReminderNotificationDeliveryResult result = TelegramDeliveryFailureClassifier.clientFailure(
                new RestClientException("read failed", new SocketTimeoutException("read timed out"))
        );

        assertTrue(result.retryableFailure());
        assertEquals("telegram.transport_error", result.reason());
    }

    private static void assertRetryable(int statusCode, String expectedReason) {
        ReminderNotificationDeliveryResult result = TelegramDeliveryFailureClassifier.httpFailure(statusCode);

        assertTrue(result.retryableFailure());
        assertEquals(expectedReason, result.reason());
    }

    private static void assertPermanent(int statusCode, String expectedReason) {
        ReminderNotificationDeliveryResult result = TelegramDeliveryFailureClassifier.httpFailure(statusCode);

        assertTrue(result.permanentFailure());
        assertEquals(expectedReason, result.reason());
    }
}
