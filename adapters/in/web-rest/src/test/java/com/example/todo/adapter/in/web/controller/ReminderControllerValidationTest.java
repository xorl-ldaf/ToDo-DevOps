package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderControllerValidationTest {
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final Instant REMIND_AT = Instant.parse("2026-04-20T11:00:00Z");
    private static final UUID TASK_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private CreateReminderUseCase createReminderUseCase;
    private ListTaskRemindersUseCase listTaskRemindersUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createReminderUseCase = mock(CreateReminderUseCase.class);
        listTaskRemindersUseCase = mock(ListTaskRemindersUseCase.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReminderController(createReminderUseCase, listTaskRemindersUseCase)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReminderShouldReturnFieldErrorForMissingRemindAt() throws Exception {
        mockMvc.perform(post("/api/tasks/{taskId}/reminders", TASK_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path", is("/api/tasks/" + TASK_UUID + "/reminders")))
                .andExpect(jsonPath("$.fieldErrors.remindAt", notNullValue()));
    }

    @Test
    void createReminderShouldReturnNotFoundForMissingTask() throws Exception {
        when(createReminderUseCase.createReminder(any()))
                .thenThrow(new ResourceNotFoundException("task not found: " + TASK_UUID));

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", TASK_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "2026-04-20T11:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/tasks/" + TASK_UUID + "/reminders")))
                .andExpect(jsonPath("$.message", is("task not found: " + TASK_UUID)));
    }

    @Test
    void createReminderShouldReturnBadRequestForInvalidReminderTime() throws Exception {
        when(createReminderUseCase.createReminder(any()))
                .thenThrow(new ApplicationValidationException("remindAt must not be in the past"));

        mockMvc.perform(post("/api/tasks/{taskId}/reminders", TASK_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "2026-04-20T09:59:59Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("remindAt must not be in the past")))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void listRemindersShouldReturnResponseDtoWithoutPersistenceFields() throws Exception {
        TaskId taskId = new TaskId(TASK_UUID);
        Reminder reminder = Reminder.schedule(taskId, REMIND_AT, NOW);
        when(listTaskRemindersUseCase.listTaskReminders(taskId)).thenReturn(List.of(reminder));

        mockMvc.perform(get("/api/tasks/{taskId}/reminders", TASK_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].taskId", is(TASK_UUID.toString())))
                .andExpect(jsonPath("$[0].remindAt", is(REMIND_AT.toString())))
                .andExpect(jsonPath("$[0].version").doesNotExist())
                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
    }
}
