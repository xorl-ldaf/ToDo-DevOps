package com.example.todo;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = {WebApplication.class, ReminderTelegramIntegrationTest.TestClockConfig.class})
@Testcontainers
@ActiveProfiles("test")
class ReminderTelegramIntegrationTest {
    private static final Instant INITIAL_TIME = Instant.parse("2026-04-21T10:00:00Z");
    private static final Instant REMIND_AT = Instant.parse("2026-04-21T10:30:00Z");
    private static final LinkedBlockingQueue<String> TELEGRAM_REQUEST_BODIES = new LinkedBlockingQueue<>();
    private static volatile HttpServer telegramServer;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("todo.telegram.enabled", () -> "true");
        registry.add("todo.telegram.bot-token", () -> "integration-test-token");
        registry.add("todo.telegram.base-url", () -> "http://127.0.0.1:" + telegramServer().getAddress().getPort());
        registry.add("todo.reminder-delivery.enabled", () -> "false");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock testClock;

    @Autowired
    private com.example.todo.application.port.in.ScanDueRemindersUseCase scanDueRemindersUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE reminders, tasks, users CASCADE");
        TELEGRAM_REQUEST_BODIES.clear();
        testClock.setInstant(INITIAL_TIME);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterAll
    static void tearDownServer() {
        if (telegramServer != null) {
            telegramServer.stop(0);
            telegramServer = null;
        }
    }

    @Test
    void scanDueRemindersShouldDeliverReminderViaTelegramAdapter() throws Exception {
        String userId = createUser("telegram.user", "Telegram User", 123456789L);
        String taskId = createTask("Review prod deploy", userId);
        String reminderId = createReminder(taskId, REMIND_AT.toString());

        testClock.setInstant(REMIND_AT);
        int deliveredCount = scanDueRemindersUseCase.scanAndPublishDueReminders(REMIND_AT);

        assertEquals(1, deliveredCount);
        String outboundBody = TELEGRAM_REQUEST_BODIES.poll(5, TimeUnit.SECONDS);
        assertNotNull(outboundBody);
        assertTrue(outboundBody.contains("\"chat_id\":123456789"));
        assertTrue(outboundBody.contains("\"text\":\"Reminder: Review prod deploy"));
        assertTrue(outboundBody.contains("Assignee: Telegram User"));
        assertTrue(outboundBody.contains("Remind at: 2026-04-21T10:30:00Z"));

        String storedStatus = jdbcTemplate.queryForObject(
                "select status from reminders where id = ?::uuid",
                String.class,
                reminderId
        );
        assertEquals("PUBLISHED", storedStatus);
    }

    private String createUser(String username, String displayName, Long telegramChatId) throws Exception {
        String requestBody = """
                {
                  "username": "%s",
                  "displayName": "%s",
                  "telegramChatId": %d
                }
                """.formatted(username, displayName, telegramChatId);

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTask(String title, String authorId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Check release rollout",
                                  "authorId": "%s"
                                }
                                """.formatted(title, authorId)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createReminder(String taskId, String remindAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "%s"
                                }
                                """.formatted(remindAt)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static synchronized HttpServer telegramServer() {
        if (telegramServer != null) {
            return telegramServer;
        }

        try {
            telegramServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start Telegram stub server", exception);
        }
        telegramServer.createContext("/botintegration-test-token/sendMessage", exchange -> {
            TELEGRAM_REQUEST_BODIES.add(readBody(exchange));
            writeJson(exchange, 200, """
                    {"ok":true,"result":{"message_id":1}}
                    """);
        });
        telegramServer.start();
        return telegramServer;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(INITIAL_TIME, ZoneOffset.UTC);
        }
    }

    static final class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone;

        private MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = currentInstant;
            this.zone = zone;
        }

        private void setInstant(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
