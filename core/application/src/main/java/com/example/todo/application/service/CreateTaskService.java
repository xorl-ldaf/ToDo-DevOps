package com.example.todo.application.service;

import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;

import java.time.Clock;
import java.util.Objects;

public class CreateTaskService implements CreateTaskUseCase {
    private final LoadUserPort loadUserPort;
    private final SaveTaskPort saveTaskPort;
    private final Clock clock;

    public CreateTaskService(
            LoadUserPort loadUserPort,
            SaveTaskPort saveTaskPort,
            Clock clock
    ) {
        this.loadUserPort = Objects.requireNonNull(loadUserPort, "loadUserPort must not be null");
        this.saveTaskPort = Objects.requireNonNull(saveTaskPort, "saveTaskPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Task createTask(CreateTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        if (command.authorId() == null) {
            throw new ApplicationValidationException("authorId must not be null");
        }

        if (!loadUserPort.existsById(command.authorId())) {
            throw new ResourceNotFoundException("author not found: " + command.authorId().value());
        }

        if (command.assigneeId() != null && !loadUserPort.existsById(command.assigneeId())) {
            throw new ResourceNotFoundException("assignee not found: " + command.assigneeId().value());
        }

        Task task = Task.createNew(
                command.authorId(),
                command.assigneeId(),
                command.title(),
                command.description(),
                command.priority(),
                command.dueAt(),
                clock.instant()
        );

        return saveTaskPort.save(task);
    }
}
