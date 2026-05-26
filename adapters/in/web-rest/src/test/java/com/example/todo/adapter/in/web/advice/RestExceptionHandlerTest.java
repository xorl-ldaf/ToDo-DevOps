package com.example.todo.adapter.in.web.advice;

import com.example.todo.application.exception.AlreadyExistsException;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void applicationValidationShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/throw/application-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.errorCode", is("APPLICATION_VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is("invalid command")))
                .andExpect(jsonPath("$.path", is("/throw/application-validation")))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void resourceNotFoundShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/throw/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("task not found")));
    }

    @Test
    void conflictShouldReturnConflict() throws Exception {
        mockMvc.perform(get("/throw/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.errorCode", is("ALREADY_EXISTS")))
                .andExpect(jsonPath("$.message", is("username already exists")));
    }

    @Test
    void invalidStateShouldReturnConflict() throws Exception {
        mockMvc.perform(get("/throw/invalid-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATE")))
                .andExpect(jsonPath("$.message", is("task cannot be reassigned")));
    }

    @Test
    void unexpectedExceptionShouldReturnGenericInternalServerError() throws Exception {
        mockMvc.perform(get("/throw/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.errorCode", is("INTERNAL_ERROR")))
                .andExpect(jsonPath("$.message", is("unexpected internal error")))
                .andExpect(jsonPath("$.message", not(containsString("database password"))))
                .andExpect(jsonPath("$.message", startsWith("unexpected")))
                .andExpect(jsonPath("$.path", is("/throw/unexpected")));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/throw/application-validation")
        String applicationValidation() {
            throw new ApplicationValidationException("invalid command");
        }

        @GetMapping("/throw/not-found")
        String notFound() {
            throw new ResourceNotFoundException("task not found");
        }

        @GetMapping("/throw/conflict")
        String conflict() {
            throw new AlreadyExistsException("username already exists");
        }

        @GetMapping("/throw/invalid-state")
        String invalidState() {
            throw new InvalidStateTransitionException("task cannot be reassigned");
        }

        @GetMapping("/throw/unexpected")
        String unexpected() {
            throw new IllegalStateException("database password is secret");
        }
    }
}
