package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.in.GetTaskUseCase;
import com.example.todo.application.port.in.ListTasksUseCase;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerValidationTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final UserId AUTHOR_ID = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UUID TASK_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private CreateTaskUseCase createTaskUseCase;
    private AssignTaskUseCase assignTaskUseCase;
    private GetTaskUseCase getTaskUseCase;
    private ListTasksUseCase listTasksUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createTaskUseCase = mock(CreateTaskUseCase.class);
        assignTaskUseCase = mock(AssignTaskUseCase.class);
        getTaskUseCase = mock(GetTaskUseCase.class);
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
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.path", is("/api/tasks")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("title")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(createTaskUseCase);
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
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("authorId")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(createTaskUseCase);
    }

    @Test
    void createTaskShouldReturnFieldErrorForTooLongTitle() throws Exception {
        String title = "a".repeat(201);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "authorId": "11111111-1111-1111-1111-111111111111"
                                }
                                """.formatted(title)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("title")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(createTaskUseCase);
    }

    @Test
    void assignTaskShouldReturnFieldErrorForMissingAssignee() throws Exception {
        mockMvc.perform(patch("/api/tasks/{taskId}/assign", TASK_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("validation failed")))
                .andExpect(jsonPath("$.path", is("/api/tasks/" + TASK_UUID + "/assign")))
                .andExpect(jsonPath("$.validationErrors", hasSize(1)))
                .andExpect(jsonPath("$.validationErrors[0].field", is("assigneeId")))
                .andExpect(jsonPath("$.validationErrors[0].message").isString());
        verifyNoInteractions(assignTaskUseCase);
    }

    @Test
    void listTasksShouldReturnResponseDtoWithoutPersistenceFields() throws Exception {
        Task task = new Task(
                new TaskId(TASK_UUID),
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
        when(listTasksUseCase.listTasks(new PageQuery(0, 20, null), null, null))
                .thenReturn(new PageResult<>(List.of(task), 0, 20, 1, 1));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is("22222222-2222-2222-2222-222222222222")))
                .andExpect(jsonPath("$.items[0].authorId", is(AUTHOR_ID.value().toString())))
                .andExpect(jsonPath("$.items[0].title", is("Write tests")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.items[0].hibernateLazyInitializer").doesNotExist());

        verify(listTasksUseCase).listTasks(new PageQuery(0, 20, null), null, null);
    }

    @Test
    void listTasksShouldAcceptMaxPageSize() throws Exception {
        when(listTasksUseCase.listTasks(new PageQuery(0, 100, null), null, null))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/api/tasks").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size", is(100)));

        verify(listTasksUseCase).listTasks(new PageQuery(0, 100, null), null, null);
    }

    @Test
    void listTasksShouldRejectInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/tasks").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("size must be between 1 and 100")))
                .andExpect(jsonPath("$.validationErrors", hasSize(0)));

        verifyNoInteractions(listTasksUseCase);
    }

    @Test
    void listTasksShouldPassSortAndFiltersToUseCase() throws Exception {
        when(listTasksUseCase.listTasks(
                new PageQuery(2, 10, "createdAt,desc"),
                TaskStatus.OPEN,
                TaskPriority.HIGH
        )).thenReturn(new PageResult<>(List.of(), 2, 10, 0, 0));

        mockMvc.perform(get("/api/tasks")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", "createdAt,desc")
                        .param("status", "OPEN")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.size", is(10)));

        verify(listTasksUseCase).listTasks(
                new PageQuery(2, 10, "createdAt,desc"),
                TaskStatus.OPEN,
                TaskPriority.HIGH
        );
    }

    @Test
    void getTaskShouldReturnNotFoundFromUseCase() throws Exception {
        when(getTaskUseCase.getRequiredTask(new TaskId(TASK_UUID)))
                .thenThrow(new ResourceNotFoundException("task not found: " + TASK_UUID));

        mockMvc.perform(get("/api/tasks/{taskId}", TASK_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/tasks/" + TASK_UUID)))
                .andExpect(jsonPath("$.message", is("task not found: " + TASK_UUID)))
                .andExpect(jsonPath("$.validationErrors", hasSize(0)));
    }
}
