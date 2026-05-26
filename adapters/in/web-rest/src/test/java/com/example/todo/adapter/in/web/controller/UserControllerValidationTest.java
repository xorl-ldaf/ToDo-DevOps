package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.application.factory.UserFactory;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerValidationTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final UUID USER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private CreateUserUseCase createUserUseCase;
    private GetUserUseCase getUserUseCase;
    private ListUsersUseCase listUsersUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createUserUseCase = mock(CreateUserUseCase.class);
        getUserUseCase = mock(GetUserUseCase.class);
        listUsersUseCase = mock(ListUsersUseCase.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(createUserUseCase, getUserUseCase, listUsersUseCase)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createUserShouldReturnFieldErrorForBlankUsername() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " ",
                                  "displayName": "Alice"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.path", is("/api/users")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("username")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(createUserUseCase);
    }

    @Test
    void createUserShouldReturnFieldErrorForMissingDisplayName() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("displayName")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(createUserUseCase);
    }

    @Test
    void createUserShouldReturnFieldErrorForTooLongUsername() throws Exception {
        String username = "a".repeat(65);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "displayName": "Alice"
                                }
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("username")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(createUserUseCase);
    }

    @Test
    void listUsersShouldReturnResponseDtoWithoutPersistenceFields() throws Exception {
        when(listUsersUseCase.listUsers(new PageQuery(0, 20, null)))
                .thenReturn(new PageResult<>(
                        List.of(new UserFactory().create("alice", "Alice", null, NOW)),
                        0,
                        20,
                        1,
                        1
                ));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", notNullValue()))
                .andExpect(jsonPath("$.items[0].username", is("alice")))
                .andExpect(jsonPath("$.items[0].displayName", is("Alice")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.items[0].hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void createUserShouldRejectNonPositiveTelegramChatIdBeforeUseCase() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "displayName": "Alice",
                                  "telegramChatId": 0
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("telegramChatId")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(createUserUseCase);
    }

    @Test
    void getUserShouldReturnNotFoundFromUseCase() throws Exception {
        when(getUserUseCase.getRequiredUser(new UserId(USER_UUID)))
                .thenThrow(new ResourceNotFoundException("user not found: " + USER_UUID));

        mockMvc.perform(get("/api/users/{userId}", USER_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/users/" + USER_UUID)))
                .andExpect(jsonPath("$.message", is("user not found: " + USER_UUID)))
                .andExpect(jsonPath("$.validationErrors", hasSize(0)));
    }
}
