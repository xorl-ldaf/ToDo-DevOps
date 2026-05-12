package com.example.todo.adapter.out.telegram;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramReminderNotificationSenderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void deliverShouldPostTelegramPayload() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {"ok":true,"result":{"message_id":42}}
                    """);
        });
        server.start();

        TelegramReminderNotificationSender sender = sender();

        ReminderNotificationDeliveryResult result = sender.deliver(notification());

        assertTrue(result.deliveredSuccessfully());
        assertEquals("/bottest-token/sendMessage", requestPath.get());
        assertTrue(requestBody.get().contains("\"chat_id\":123456789"));
        assertTrue(requestBody.get().contains("\"text\":\"Reminder: Review deployment"));
        assertTrue(requestBody.get().contains("Assignee: Alice DevOps"));
        assertTrue(requestBody.get().contains("Remind at: 2026-04-21T10:30:00Z"));
        assertTrue(requestBody.get().contains("Description: Check prod rollout"));
    }

    @Test
    void deliverShouldClassifyTransientTelegramFailureWithoutInternalRetryLoop() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            attempts.incrementAndGet();
            writeJson(exchange, 500, """
                    {"ok":false,"description":"internal server error"}
                    """);
        });
        server.start();

        TelegramReminderNotificationSender sender = sender();

        ReminderNotificationDeliveryResult result = sender.deliver(notification());

        assertTrue(result.retryableFailure());
        assertEquals("telegram HTTP 500", result.reason());
        assertEquals(1, attempts.get());
    }

    @Test
    void deliverShouldStopOnPermanentTelegramFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            attempts.incrementAndGet();
            writeJson(exchange, 400, """
                    {"ok":false,"description":"bad request"}
                    """);
        });
        server.start();

        TelegramReminderNotificationSender sender = sender();

        ReminderNotificationDeliveryResult result = sender.deliver(notification());

        assertTrue(result.permanentFailure());
        assertFalse(result.deliveredSuccessfully());
        assertEquals(1, attempts.get());
    }

    @Test
    void deliverShouldClassifyForbiddenTelegramChatAsPermanentFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            attempts.incrementAndGet();
            writeJson(exchange, 403, """
                    {"ok":false,"description":"Forbidden: bot was blocked by the user"}
                    """);
        });
        server.start();

        ReminderNotificationDeliveryResult result = sender().deliver(notification());

        assertTrue(result.permanentFailure());
        assertEquals("telegram chat forbidden HTTP 403", result.reason());
        assertEquals(1, attempts.get());
    }

    @Test
    void deliverShouldClassifyTooManyRequestsAsRetryableFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            attempts.incrementAndGet();
            writeJson(exchange, 429, """
                    {"ok":false,"description":"Too Many Requests"}
                    """);
        });
        server.start();

        ReminderNotificationDeliveryResult result = sender().deliver(notification());

        assertTrue(result.retryableFailure());
        assertEquals("telegram HTTP 429", result.reason());
        assertEquals(1, attempts.get());
    }

    @Test
    void deliverShouldClassifyReadTimeoutAsRetryableFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            attempts.incrementAndGet();
            try {
                Thread.sleep(500);
                writeJson(exchange, 200, """
                        {"ok":true}
                        """);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        TelegramReminderNotificationSender sender = new TelegramReminderNotificationSender(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-token",
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                new SimpleMeterRegistry()
        );

        ReminderNotificationDeliveryResult result = sender.deliver(notification());

        assertTrue(result.retryableFailure());
        assertEquals(1, attempts.get());
    }

    private ReminderNotificationV1 notification() {
        return new ReminderNotificationV1(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                ReminderNotificationV1.NOTIFICATION_TYPE,
                ReminderNotificationV1.NOTIFICATION_VERSION,
                Instant.parse("2026-04-21T10:30:00Z"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "Review deployment",
                "Check prod rollout",
                Instant.parse("2026-04-21T10:30:00Z"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "Alice DevOps",
                123456789L
        );
    }

    private TelegramReminderNotificationSender sender() {
        return new TelegramReminderNotificationSender(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-token",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new SimpleMeterRegistry()
        );
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
