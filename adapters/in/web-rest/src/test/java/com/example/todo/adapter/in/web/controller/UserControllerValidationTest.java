package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerValidationTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    private CreateUserUseCase createUserUseCase;
    private ListUsersUseCase listUsersUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createUserUseCase = mock(CreateUserUseCase.class);
        GetUserUseCase getUserUseCase = mock(GetUserUseCase.class);
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
                .andExpect(jsonPath("$.fieldErrors.username", notNullValue()));
    }

    @Test
    void listUsersShouldReturnResponseDtoWithoutPersistenceFields() throws Exception {
        when(listUsersUseCase.listUsers()).thenReturn(List.of(
                User.createNew("alice", "Alice", null, NOW)
        ));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].username", is("alice")))
                .andExpect(jsonPath("$[0].displayName", is("Alice")))
                .andExpect(jsonPath("$[0].version").doesNotExist())
                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
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
                .andExpect(jsonPath("$.fieldErrors.telegramChatId", notNullValue()));
    }
}
