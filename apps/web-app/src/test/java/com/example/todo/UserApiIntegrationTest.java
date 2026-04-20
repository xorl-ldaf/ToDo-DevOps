package com.example.todo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = WebApplication.class)
@Testcontainers
@ActiveProfiles("test")
class UserApiIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE reminders, tasks, users CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createUserShouldReturnCreatedUserWithTelegramChatId() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice.integration",
                                  "displayName": "Alice Integration",
                                  "telegramChatId": 123456789
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", is("alice.integration")))
                .andExpect(jsonPath("$.displayName", is("Alice Integration")))
                .andExpect(jsonPath("$.telegramChatId", is(123456789)));
    }

    @Test
    void listUsersShouldContainCreatedUser() throws Exception {
        createUser("list.user", "List User", null);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].username", is("list.user")))
                .andExpect(jsonPath("$[0].displayName", is("List User")))
                .andExpect(jsonPath("$[0].telegramChatId").doesNotExist());
    }

    @Test
    void getUserShouldReturnExistingUser() throws Exception {
        String userId = createUser("get.user", "Get User", 555000111L);

        mockMvc.perform(get("/api/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId)))
                .andExpect(jsonPath("$.username", is("get.user")))
                .andExpect(jsonPath("$.displayName", is("Get User")))
                .andExpect(jsonPath("$.telegramChatId", is(555000111)));
    }

    @Test
    void getUserShouldReturnNotFoundForMissingUser() throws Exception {
        UUID missingUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(get("/api/users/{userId}", missingUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("user not found: " + missingUserId)))
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    void createUserShouldReturnValidationErrorForBlankUsername() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "   ",
                                  "displayName": "Broken User",
                                  "telegramChatId": 777
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors.username", notNullValue()));
    }

    @Test
    void createUserShouldReturnConflictForDuplicateUsername() throws Exception {
        createUser("duplicate.user", "First Duplicate User", null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "duplicate.user",
                                  "displayName": "Second Duplicate User",
                                  "telegramChatId": 888
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("username already exists: duplicate.user")))
                .andExpect(jsonPath("$.validationErrors").isMap());
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
}
