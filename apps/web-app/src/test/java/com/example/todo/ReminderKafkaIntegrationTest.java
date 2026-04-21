package com.example.todo;

import com.example.todo.application.event.ReminderScheduledEventV1;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = {WebApplication.class, ReminderKafkaIntegrationTest.TestClockConfig.class})
@Testcontainers
@ActiveProfiles("test")
class ReminderKafkaIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-02-15T10:00:00Z");
    private static final Instant REMIND_AT = Instant.parse("2026-02-15T12:00:00Z");
    private static final String TOPIC = "todo.reminder.scheduled.v1.test";
    private static final String CONSUMER_GROUP_ID = "todo-web-app-reminder-scheduled-v1-test";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("todo.kafka.enabled", () -> "true");
        registry.add("todo.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("todo.kafka.consumer-group-id", () -> CONSUMER_GROUP_ID);
        registry.add("todo.kafka.topics.reminder-scheduled-v1", () -> TOPIC);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MutableClock testClock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE reminders, tasks, users CASCADE");
        testClock.setInstant(INITIAL_TIME);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createReminderShouldPublishAndConsumeReminderScheduledEvent() throws Exception {
        String taskId = createTaskForReminder("reminder.kafka.author", "Reminder Kafka Author", "Kafka reminder task");

        createReminder(taskId, REMIND_AT.toString());

        Counter consumedCounter = awaitCounter(
                "todo.reminder.scheduled.events.consumed",
                TOPIC,
                ReminderScheduledEventV1.EVENT_VERSION
        );
        assertEquals(1.0d, consumedCounter.count());

        Timer lagTimer = meterRegistry.find("todo.reminder.scheduled.event.consume.lag")
                .tags("topic", TOPIC, "event_version", ReminderScheduledEventV1.EVENT_VERSION)
                .timer();
        assertNotNull(lagTimer);
        assertEquals(1L, lagTimer.count());
    }

    private Counter awaitCounter(String name, String topic, String eventVersion) throws InterruptedException {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            Counter counter = meterRegistry.find(name)
                    .tags("topic", topic, "event_version", eventVersion)
                    .counter();
            if (counter != null && counter.count() >= 1.0d) {
                return counter;
            }
            Thread.sleep(200L);
        }
        fail("Timed out waiting for Kafka consumer metric " + name);
        return null;
    }

    private String createTaskForReminder(String username, String displayName, String taskTitle) throws Exception {
        String authorId = createUser(username, displayName);
        return createTask(taskTitle, authorId);
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
                        .content(createTaskRequest(title, authorId)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createReminder(String taskId, String remindAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReminderRequest(remindAt)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private String createTaskRequest(String title, String authorId) {
        return """
                {
                  "title": "%s",
                  "description": "Reminder kafka integration task",
                  "authorId": "%s"
                }
                """.formatted(title, authorId);
    }

    private String createReminderRequest(String remindAt) {
        return """
                {
                  "remindAt": "%s"
                }
                """.formatted(remindAt);
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
