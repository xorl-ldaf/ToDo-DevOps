package com.example.todo.adapter.in.web.controller;

import com.example.todo.adapter.in.web.dto.AssignTaskRequest;
import com.example.todo.adapter.in.web.dto.CreateTaskRequest;
import com.example.todo.adapter.in.web.dto.PageResponse;
import com.example.todo.adapter.in.web.dto.TaskPriorityDto;
import com.example.todo.adapter.in.web.dto.TaskResponse;
import com.example.todo.adapter.in.web.dto.TaskStatusDto;
import com.example.todo.adapter.in.web.mapper.WebApiMapper;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.in.GetTaskUseCase;
import com.example.todo.application.port.in.ListTasksUseCase;
import com.example.todo.application.query.PageQuery;
import com.example.todo.domain.task.TaskId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@Validated
public class TaskController {

    private final CreateTaskUseCase createTaskUseCase;
    private final AssignTaskUseCase assignTaskUseCase;
    private final GetTaskUseCase getTaskUseCase;
    private final ListTasksUseCase listTasksUseCase;

    public TaskController(
            CreateTaskUseCase createTaskUseCase,
            AssignTaskUseCase assignTaskUseCase,
            GetTaskUseCase getTaskUseCase,
            ListTasksUseCase listTasksUseCase
    ) {
        this.createTaskUseCase = createTaskUseCase;
        this.assignTaskUseCase = assignTaskUseCase;
        this.getTaskUseCase = getTaskUseCase;
        this.listTasksUseCase = listTasksUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        return WebApiMapper.toResponse(
                createTaskUseCase.createTask(WebApiMapper.toCommand(request))
        );
    }

    @GetMapping
    public PageResponse<TaskResponse> listTasks(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "status", required = false) TaskStatusDto status,
            @RequestParam(name = "priority", required = false) TaskPriorityDto priority
    ) {
        return WebApiMapper.toResponse(
                listTasksUseCase.listTasks(
                        new PageQuery(page, size, sort),
                        WebApiMapper.toDomain(status),
                        WebApiMapper.toDomain(priority)
                ),
                WebApiMapper::toResponse
        );
    }

    @GetMapping("/{taskId}")
    public TaskResponse getTask(@PathVariable("taskId") UUID taskId) {
        return WebApiMapper.toResponse(getTaskUseCase.getRequiredTask(new TaskId(taskId)));
    }

    @PatchMapping("/{taskId}/assign")
    public TaskResponse assignTask(
            @PathVariable("taskId") UUID taskId,
            @Valid @RequestBody AssignTaskRequest request
    ) {
        return WebApiMapper.toResponse(
                assignTaskUseCase.assignTask(WebApiMapper.toCommand(taskId, request))
        );
    }
}
