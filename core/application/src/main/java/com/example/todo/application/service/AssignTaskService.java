package com.example.todo.application.service;

import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;

import java.time.Clock;
import java.util.Objects;

public class AssignTaskService implements AssignTaskUseCase {
    private final LoadTaskPort loadTaskPort;
    private final LoadUserPort loadUserPort;
    private final SaveTaskPort saveTaskPort;
    private final Clock clock;

    public AssignTaskService(
            LoadTaskPort loadTaskPort,
            LoadUserPort loadUserPort,
            SaveTaskPort saveTaskPort,
            Clock clock
    ) {
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
        this.loadUserPort = Objects.requireNonNull(loadUserPort, "loadUserPort must not be null");
        this.saveTaskPort = Objects.requireNonNull(saveTaskPort, "saveTaskPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Task assignTask(AssignTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        if (command.taskId() == null) {
            throw new ApplicationValidationException("taskId must not be null");
        }
        if (command.assigneeId() == null) {
            throw new ApplicationValidationException("assigneeId must not be null");
        }

        Task task = loadTaskPort.loadById(command.taskId())
                .orElseThrow(() -> new ResourceNotFoundException("task not found: " + command.taskId().value()));

        if (!loadUserPort.existsById(command.assigneeId())) {
            throw new ResourceNotFoundException("assignee not found: " + command.assigneeId().value());
        }

        task.assignTo(command.assigneeId(), clock.instant());

        return saveTaskPort.save(task);
    }
}
