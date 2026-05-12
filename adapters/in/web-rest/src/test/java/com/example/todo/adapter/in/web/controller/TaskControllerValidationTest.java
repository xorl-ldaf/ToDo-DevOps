package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.in.GetTaskUseCase;
import com.example.todo.application.port.in.ListTasksUseCase;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerValidationTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final UserId AUTHOR_ID = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private ListTasksUseCase listTasksUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CreateTaskUseCase createTaskUseCase = mock(CreateTaskUseCase.class);
        AssignTaskUseCase assignTaskUseCase = mock(AssignTaskUseCase.class);
        GetTaskUseCase getTaskUseCase = mock(GetTaskUseCase.class);
        listTasksUseCase = mock(ListTasksUseCase.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TaskController(createTaskUseCase, assignTaskUseCase, getTaskUseCase, listTasksUseCase)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createTaskShouldReturnFieldErrorForBlankTitle() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "authorId": "11111111-1111-1111-1111-111111111111"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path", is("/api/tasks")))
                .andExpect(jsonPath("$.fieldErrors.title", notNullValue()));
    }

    @Test
    void createTaskShouldReturnFieldErrorForMissingAuthor() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Write tests"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.authorId", notNullValue()));
    }

    @Test
    void listTasksShouldReturnResponseDtoWithoutPersistenceFields() throws Exception {
        Task task = Task.restore(
                new TaskId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                AUTHOR_ID,
                AUTHOR_ID,
                "Write tests",
                "Controller validation",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                NOW,
                NOW
        );
        when(listTasksUseCase.listTasks()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is("22222222-2222-2222-2222-222222222222")))
                .andExpect(jsonPath("$[0].authorId", is(AUTHOR_ID.value().toString())))
                .andExpect(jsonPath("$[0].title", is("Write tests")))
                .andExpect(jsonPath("$[0].version").doesNotExist())
                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
    }
}
