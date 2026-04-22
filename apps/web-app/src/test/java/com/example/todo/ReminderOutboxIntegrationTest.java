package com.example.todo;

import com.example.todo.application.port.in.FlushReminderScheduledEventOutboxUseCase;
import com.example.todo.application.port.in.ReminderScheduledEventOutboxReport;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import com.jayway.jsonpath.JsonPath;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = {WebApplication.class, ReminderOutboxIntegrationTest.TestConfig.class})
@Testcontainers
@ActiveProfiles("test")
class ReminderOutboxIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-04-21T10:00:00Z");
    private static final Instant REMIND_AT = Instant.parse("2026-04-21T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("todo.kafka.enabled", () -> "true");
        registry.add("todo.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("todo.kafka.outbox.initial-delay", () -> "1h");
        registry.add("todo.kafka.outbox.fixed-delay", () -> "1h");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FlushReminderScheduledEventOutboxUseCase flushReminderScheduledEventOutboxUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE reminder_scheduled_event_receipts, reminder_scheduled_event_outbox, reminders, tasks, users CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createReminderShouldPersistOutboxEntryAndKeepItRetryableWhenPublishFails() throws Exception {
        String authorId = createUser("outbox.user", "Outbox User");
        String taskId = createTask("Persist outbox reminder", authorId);

        createReminder(taskId, REMIND_AT.toString());

        Integer storedOutboxCount = jdbcTemplate.queryForObject(
                "select count(*) from reminder_scheduled_event_outbox where status = 'PENDING'",
                Integer.class
        );
        assertEquals(1, storedOutboxCount);

        ReminderScheduledEventOutboxReport report = flushReminderScheduledEventOutboxUseCase.flush(REMIND_AT);

        assertEquals(1, report.retriedCount());
        Integer retriedOutboxCount = jdbcTemplate.queryForObject(
                "select count(*) from reminder_scheduled_event_outbox where status = 'PENDING' and delivery_attempts = 1",
                Integer.class
        );
        assertEquals(1, retriedOutboxCount);
    }

    private String createUser(String username, String displayName) throws Exception {
        String requestBody = """
                {
                  "username": "%s",
                  "displayName": "%s"
                }
                """.formatted(username, displayName);

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
                                  "description": "Outbox retry integration test",
                                  "authorId": "%s"
                                }
                                """.formatted(title, authorId)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createReminder(String taskId, String remindAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "%s"
                                }
                                """.formatted(remindAt)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        PublishReminderScheduledEventPort failingPublishReminderScheduledEventPort() {
            return event -> {
                throw new IllegalStateException("simulated kafka publish failure");
            };
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
