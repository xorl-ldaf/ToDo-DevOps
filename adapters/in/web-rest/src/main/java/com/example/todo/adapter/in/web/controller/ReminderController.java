package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.dto.CreateReminderRequest;
import com.example.todo.adapter.in.web.dto.ReminderResponse;
import com.example.todo.adapter.in.web.mapper.WebApiMapper;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.domain.task.TaskId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/reminders")
@Validated
public class ReminderController {

    private final CreateReminderUseCase createReminderUseCase;
    private final ListTaskRemindersUseCase listTaskRemindersUseCase;

    public ReminderController(
            CreateReminderUseCase createReminderUseCase,
            ListTaskRemindersUseCase listTaskRemindersUseCase
    ) {
        this.createReminderUseCase = createReminderUseCase;
        this.listTaskRemindersUseCase = listTaskRemindersUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse createReminder(
            @PathVariable("taskId") UUID taskId,
            @Valid @RequestBody CreateReminderRequest request
    ) {
        return WebApiMapper.toResponse(
                createReminderUseCase.createReminder(WebApiMapper.toCommand(taskId, request))
        );
    }

    @GetMapping
    public List<ReminderResponse> listTaskReminders(@PathVariable("taskId") UUID taskId) {
        return listTaskRemindersUseCase.listTaskReminders(new TaskId(taskId))
                .stream()
                .map(WebApiMapper::toResponse)
                .toList();
    }
}
