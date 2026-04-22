package com.example.todo;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {WebApplication.class, ReminderApiIntegrationTest.TestClockConfig.class})
@Testcontainers
@ActiveProfiles("test")
class ReminderApiIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-01-10T09:00:00Z");
    private static final Instant VALID_REMIND_AT = Instant.parse("2026-01-10T11:30:00Z");
    private static final Instant SECOND_REMIND_AT = Instant.parse("2026-01-10T13:45:00Z");
    private static final Instant PAST_REMIND_AT = Instant.parse("2026-01-10T08:59:59Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void createReminderShouldReturnCreatedReminderForExistingTask() throws Exception {
        String taskId = createTaskForReminder("reminder.author.create", "Reminder Author Create", "Create reminder task");

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReminderRequest(VALID_REMIND_AT.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.taskId", is(taskId)))
                .andExpect(jsonPath("$.remindAt", is(VALID_REMIND_AT.toString())))
                .andExpect(jsonPath("$.status", is("SCHEDULED")))
                .andExpect(jsonPath("$.createdAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$.updatedAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$.deliveredAt").doesNotExist());
    }

    @Test
    void listTaskRemindersShouldContainCreatedReminder() throws Exception {
        String taskId = createTaskForReminder("reminder.author.list", "Reminder Author List", "List reminder task");
        String reminderId = createReminder(taskId, VALID_REMIND_AT.toString());

        mockMvc.perform(get("/api/tasks/{taskId}/reminders", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(reminderId)))
                .andExpect(jsonPath("$[0].taskId", is(taskId)))
                .andExpect(jsonPath("$[0].remindAt", is(VALID_REMIND_AT.toString())))
                .andExpect(jsonPath("$[0].status", is("SCHEDULED")))
                .andExpect(jsonPath("$[0].createdAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$[0].updatedAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$[0].deliveredAt").doesNotExist());
    }

    @Test
    void listTaskRemindersShouldReturnOnlyRemindersForRequestedTask() throws Exception {
        String firstTaskId = createTaskForReminder("reminder.author.first", "Reminder Author First", "First task");
        String secondTaskId = createTaskForReminder("reminder.author.second", "Reminder Author Second", "Second task");

        createReminder(firstTaskId, VALID_REMIND_AT.toString());
        createReminder(firstTaskId, SECOND_REMIND_AT.toString());

        mockMvc.perform(get("/api/tasks/{taskId}/reminders", secondTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/tasks/{taskId}/reminders", firstTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].taskId", is(firstTaskId)))
                .andExpect(jsonPath("$[1].taskId", is(firstTaskId)));
    }

    @Test
    void createReminderShouldReturnNotFoundForMissingTask() throws Exception {
        UUID missingTaskId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", missingTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReminderRequest(VALID_REMIND_AT.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("task not found: " + missingTaskId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void listTaskRemindersShouldReturnNotFoundForMissingTask() throws Exception {
        UUID missingTaskId = UUID.fromString("88888888-8888-8888-8888-888888888888");

        mockMvc.perform(get("/api/tasks/{taskId}/reminders", missingTaskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("task not found: " + missingTaskId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void createReminderShouldReturnValidationErrorForMissingRemindAt() throws Exception {
        String taskId = createTaskForReminder("reminder.author.missing.remindAt", "Reminder Author Missing RemindAt", "Task for missing remindAt");

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors.remindAt", notNullValue()));
    }

    @Test
    void createReminderShouldReturnBadRequestForPastRemindAt() throws Exception {
        String taskId = createTaskForReminder("reminder.author.past", "Reminder Author Past", "Task for past reminder");

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReminderRequest(PAST_REMIND_AT.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("remindAt must not be in the past")))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void createReminderShouldReturnBadRequestForMalformedDateTime() throws Exception {
        String taskId = createTaskForReminder("reminder.author.malformed", "Reminder Author Malformed", "Task for malformed reminder");

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "not-an-instant"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("request body is malformed or contains invalid enum/date value")))
                .andExpect(jsonPath("$.validationErrors").isMap());
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
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTask(String title, String authorId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskRequest(title, authorId)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createReminder(String taskId, String remindAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReminderRequest(remindAt)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTaskRequest(String title, String authorId) {
        return """
                {
                  "title": "%s",
                  "description": "Reminder integration setup task",
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
