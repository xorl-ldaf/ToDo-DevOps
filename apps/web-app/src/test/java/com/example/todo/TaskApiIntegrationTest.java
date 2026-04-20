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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {WebApplication.class, TaskApiIntegrationTest.TestClockConfig.class})
@Testcontainers
@ActiveProfiles("test")
class TaskApiIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ASSIGNMENT_TIME = Instant.parse("2026-01-01T10:15:00Z");

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
    void createTaskShouldReturnCreatedTaskWithDefaultAssigneeAndPriority() throws Exception {
        String authorId = createUser("task.author.create", "Task Author Create", null);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskRequest(
                                "Document integration test baseline",
                                "Create task happy path",
                                authorId,
                                null,
                                null,
                                null
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.authorId", is(authorId)))
                .andExpect(jsonPath("$.assigneeId", is(authorId)))
                .andExpect(jsonPath("$.title", is("Document integration test baseline")))
                .andExpect(jsonPath("$.description", is("Create task happy path")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.priority", is("MEDIUM")))
                .andExpect(jsonPath("$.dueAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$.updatedAt", is(INITIAL_TIME.toString())));
    }

    @Test
    void listTasksShouldContainCreatedTask() throws Exception {
        String authorId = createUser("task.author.list", "Task Author List", null);
        String taskId = createTask(
                "List task smoke check",
                "Listed task payload",
                authorId,
                null,
                "HIGH",
                "2026-02-01T09:30:00Z"
        );

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(taskId)))
                .andExpect(jsonPath("$[0].authorId", is(authorId)))
                .andExpect(jsonPath("$[0].assigneeId", is(authorId)))
                .andExpect(jsonPath("$[0].title", is("List task smoke check")))
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[0].priority", is("HIGH")));
    }

    @Test
    void getTaskShouldReturnExistingTask() throws Exception {
        String authorId = createUser("task.author.get", "Task Author Get", null);
        String assigneeId = createUser("task.assignee.get", "Task Assignee Get", 900001L);
        String taskId = createTask(
                "Fetch single task",
                "Task details for get endpoint",
                authorId,
                assigneeId,
                "CRITICAL",
                "2026-03-10T14:45:00Z"
        );

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(taskId)))
                .andExpect(jsonPath("$.authorId", is(authorId)))
                .andExpect(jsonPath("$.assigneeId", is(assigneeId)))
                .andExpect(jsonPath("$.title", is("Fetch single task")))
                .andExpect(jsonPath("$.description", is("Task details for get endpoint")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.priority", is("CRITICAL")))
                .andExpect(jsonPath("$.dueAt", is("2026-03-10T14:45:00Z")))
                .andExpect(jsonPath("$.createdAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$.updatedAt", is(INITIAL_TIME.toString())));
    }

    @Test
    void assignTaskShouldReassignTaskAndMoveItToInProgress() throws Exception {
        String authorId = createUser("task.author.assign", "Task Author Assign", null);
        String initialAssigneeId = createUser("task.assignee.initial", "Task Assignee Initial", null);
        String newAssigneeId = createUser("task.assignee.new", "Task Assignee New", null);
        String taskId = createTask(
                "Reassign implementation task",
                "Task to be reassigned",
                authorId,
                initialAssigneeId,
                "LOW",
                null
        );

        testClock.setInstant(ASSIGNMENT_TIME);

        mockMvc.perform(patch("/api/tasks/{taskId}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTaskRequest(newAssigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(taskId)))
                .andExpect(jsonPath("$.authorId", is(authorId)))
                .andExpect(jsonPath("$.assigneeId", is(newAssigneeId)))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.priority", is("LOW")))
                .andExpect(jsonPath("$.createdAt", is(INITIAL_TIME.toString())))
                .andExpect(jsonPath("$.updatedAt", is(ASSIGNMENT_TIME.toString())));
    }

    @Test
    void getTaskShouldReturnNotFoundForMissingTask() throws Exception {
        UUID missingTaskId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        mockMvc.perform(get("/api/tasks/{taskId}", missingTaskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("task not found: " + missingTaskId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void createTaskShouldReturnValidationErrorForBlankTitle() throws Exception {
        String authorId = createUser("task.author.blank", "Task Author Blank", null);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskRequest(
                                "   ",
                                "Blank title should fail",
                                authorId,
                                null,
                                null,
                                null
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors.title", notNullValue()));
    }

    @Test
    void createTaskShouldReturnValidationErrorForMissingAuthorId() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Task without author",
                                  "description": "Missing authorId must fail"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors.authorId", notNullValue()));
    }

    @Test
    void createTaskShouldReturnNotFoundForMissingAuthor() throws Exception {
        UUID missingAuthorId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskRequest(
                                "Task with missing author",
                                "Author must exist",
                                missingAuthorId.toString(),
                                null,
                                null,
                                null
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("author not found: " + missingAuthorId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void createTaskShouldReturnNotFoundForMissingAssignee() throws Exception {
        String authorId = createUser("task.author.missing.assignee", "Task Author Missing Assignee", null);
        UUID missingAssigneeId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskRequest(
                                "Task with missing assignee",
                                "Assignee must exist when specified",
                                authorId,
                                missingAssigneeId.toString(),
                                null,
                                null
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("assignee not found: " + missingAssigneeId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void assignTaskShouldReturnNotFoundForMissingTask() throws Exception {
        String assigneeId = createUser("task.assignee.missing.task", "Task Assignee Missing Task", null);
        UUID missingTaskId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        mockMvc.perform(patch("/api/tasks/{taskId}/assign", missingTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTaskRequest(assigneeId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("task not found: " + missingTaskId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void assignTaskShouldReturnNotFoundForMissingAssignee() throws Exception {
        String authorId = createUser("task.author.assign.missing", "Task Author Assign Missing", null);
        String taskId = createTask(
                "Assign with missing assignee",
                "Patch should fail when assignee is absent",
                authorId,
                null,
                null,
                null
        );
        UUID missingAssigneeId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        mockMvc.perform(patch("/api/tasks/{taskId}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTaskRequest(missingAssigneeId.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("assignee not found: " + missingAssigneeId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void assignTaskShouldReturnValidationErrorForNullAssigneeId() throws Exception {
        String authorId = createUser("task.author.assign.null", "Task Author Assign Null", null);
        String taskId = createTask(
                "Assign with null assignee",
                "Null assigneeId should fail validation",
                authorId,
                null,
                null,
                null
        );

        mockMvc.perform(patch("/api/tasks/{taskId}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors.assigneeId", notNullValue()));
    }

    private String createUser(String username, String displayName, Long telegramChatId) throws Exception {
        String requestBody = telegramChatId == null
                ? """
                  {
                    "username": "%s",
                    "displayName": "%s"
                  }
                  """.formatted(username, displayName)
                : """
                  {
                    "username": "%s",
                    "displayName": "%s",
                    "telegramChatId": %d
                  }
                  """.formatted(username, displayName, telegramChatId);

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTask(
            String title,
            String description,
            String authorId,
            String assigneeId,
            String priority,
            String dueAt
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskRequest(title, description, authorId, assigneeId, priority, dueAt)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTaskRequest(
            String title,
            String description,
            String authorId,
            String assigneeId,
            String priority,
            String dueAt
    ) {
        StringBuilder json = new StringBuilder()
                .append("{\n")
                .append("  \"title\": \"").append(title).append("\",\n")
                .append("  \"description\": \"").append(description).append("\",\n")
                .append("  \"authorId\": \"").append(authorId).append('"');

        if (assigneeId != null) {
            json.append(",\n  \"assigneeId\": \"").append(assigneeId).append('"');
        }
        if (priority != null) {
            json.append(",\n  \"priority\": \"").append(priority).append('"');
        }
        if (dueAt != null) {
            json.append(",\n  \"dueAt\": \"").append(dueAt).append('"');
        }

        json.append("\n}\n");
        return json.toString();
    }

    private String assignTaskRequest(String assigneeId) {
        return """
                {
                  "assigneeId": "%s"
                }
                """.formatted(assigneeId);
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

        void setInstant(Instant currentInstant) {
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
