package com.example.todo;

import com.example.todo.adapter.out.persistence.adapter.ReminderScheduledEventOutboxPersistenceAdapter;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.in.FlushReminderScheduledEventOutboxUseCase;
import com.example.todo.application.port.in.ReminderScheduledEventOutboxReport;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import com.example.todo.application.port.out.StoreReminderScheduledEventPort;
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
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = {WebApplication.class, ReminderOutboxIntegrationTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
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

    @Autowired
    private FailableStoreReminderScheduledEventPort storeReminderScheduledEventPort;

    @Autowired
    private CountingFailingPublishReminderScheduledEventPort publishReminderScheduledEventPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE reminder_scheduled_event_receipts, reminder_scheduled_event_outbox, reminders, tasks, users CASCADE");
        storeReminderScheduledEventPort.reset();
        publishReminderScheduledEventPort.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createReminderShouldPersistOutboxEntryAndKeepItRetryableWhenPublishFails() throws Exception {
        String authorId = createUser("outbox.user", "Outbox User");
        String taskId = createTask("Persist outbox reminder", authorId);

        String reminderId = createReminder(taskId, REMIND_AT.toString());
        assertEquals(0, publishReminderScheduledEventPort.publishAttempts());

        Integer storedReminderCount = jdbcTemplate.queryForObject(
                "select count(*) from reminders where id = ?::uuid and task_id = ?::uuid",
                Integer.class,
                reminderId,
                taskId
        );
        assertEquals(1, storedReminderCount);

        Integer storedOutboxCount = jdbcTemplate.queryForObject(
                "select count(*) from reminder_scheduled_event_outbox where status = 'PENDING'",
                Integer.class
        );
        assertEquals(1, storedOutboxCount);

        Map<String, Object> outboxRow = jdbcTemplate.queryForMap(
                """
                        select event_type, event_version, reminder_id::text as reminder_id, task_id::text as task_id, payload
                        from reminder_scheduled_event_outbox
                        where reminder_id = ?::uuid
                        """,
                reminderId
        );
        assertEquals(ReminderScheduledEventV1.EVENT_TYPE, outboxRow.get("event_type"));
        assertEquals(ReminderScheduledEventV1.EVENT_VERSION, outboxRow.get("event_version"));
        assertEquals(reminderId, outboxRow.get("reminder_id"));
        assertEquals(taskId, outboxRow.get("task_id"));
        String payload = (String) outboxRow.get("payload");
        assertEquals(ReminderScheduledEventV1.EVENT_TYPE, JsonPath.read(payload, "$.eventType"));
        assertEquals(ReminderScheduledEventV1.EVENT_VERSION, JsonPath.read(payload, "$.eventVersion"));
        assertEquals(reminderId, JsonPath.read(payload, "$.reminderId"));
        assertEquals(taskId, JsonPath.read(payload, "$.taskId"));
        assertEquals(REMIND_AT.toString(), JsonPath.read(payload, "$.remindAt"));
        assertEquals("SCHEDULED", JsonPath.read(payload, "$.reminderStatus"));

        ReminderScheduledEventOutboxReport report = flushReminderScheduledEventOutboxUseCase.flush(REMIND_AT);

        assertEquals(1, publishReminderScheduledEventPort.publishAttempts());
        assertEquals(1, report.retriedCount());
        Integer retriedOutboxCount = jdbcTemplate.queryForObject(
                "select count(*) from reminder_scheduled_event_outbox where status = 'PENDING' and delivery_attempts = 1",
                Integer.class
        );
        assertEquals(1, retriedOutboxCount);
    }

    @Test
    void createReminderForMissingTaskShouldNotPersistReminderOrOutboxEntry() throws Exception {
        UUID missingTaskId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", missingTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "%s"
                                }
                                """.formatted(REMIND_AT)))
                .andReturn();

        assertEquals(404, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(0, tableCount("reminders"));
        assertEquals(0, tableCount("reminder_scheduled_event_outbox"));
        assertEquals(0, publishReminderScheduledEventPort.publishAttempts());
    }

    @Test
    void createReminderShouldRollBackReminderWhenOutboxStoreFails() throws Exception {
        String authorId = createUser("outbox.rollback.user", "Outbox Rollback User");
        String taskId = createTask("Rollback outbox reminder", authorId);
        storeReminderScheduledEventPort.failNextStore();

        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "%s"
                                }
                                """.formatted(REMIND_AT)))
                .andReturn();

        assertEquals(500, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(0, tableCount("reminders"));
        assertEquals(0, tableCount("reminder_scheduled_event_outbox"));
        assertEquals(0, publishReminderScheduledEventPort.publishAttempts());
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

    private int tableCount(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        FailableStoreReminderScheduledEventPort failableStoreReminderScheduledEventPort(
                ReminderScheduledEventOutboxPersistenceAdapter delegate
        ) {
            return new FailableStoreReminderScheduledEventPort(delegate);
        }

        @Bean
        @Primary
        CountingFailingPublishReminderScheduledEventPort failingPublishReminderScheduledEventPort() {
            return new CountingFailingPublishReminderScheduledEventPort();
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    static final class FailableStoreReminderScheduledEventPort implements StoreReminderScheduledEventPort {
        private final StoreReminderScheduledEventPort delegate;
        private final AtomicBoolean failNextStore = new AtomicBoolean(false);

        private FailableStoreReminderScheduledEventPort(StoreReminderScheduledEventPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void store(ReminderScheduledEventV1 event) {
            if (failNextStore.getAndSet(false)) {
                throw new IllegalStateException("simulated outbox store failure");
            }
            delegate.store(event);
        }

        private void failNextStore() {
            failNextStore.set(true);
        }

        private void reset() {
            failNextStore.set(false);
        }
    }

    static final class CountingFailingPublishReminderScheduledEventPort implements PublishReminderScheduledEventPort {
        private final AtomicInteger publishAttempts = new AtomicInteger();

        @Override
        public void publish(ReminderScheduledEventV1 event) {
            publishAttempts.incrementAndGet();
            throw new IllegalStateException("simulated kafka publish failure");
        }

        private int publishAttempts() {
            return publishAttempts.get();
        }

        private void reset() {
            publishAttempts.set(0);
        }
    }
}
