package com.example.todo.application.service;

import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;

import java.time.Clock;
import java.time.Instant;
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

        if (!loadUserPort.existsById(command.authorId())) {
            throw new IllegalArgumentException("author not found: " + command.authorId());
        }

        UserId actualAssigneeId = command.assigneeId() == null ? command.authorId() : command.assigneeId();

        if (!loadUserPort.existsById(actualAssigneeId)) {
            throw new IllegalArgumentException("assignee not found: " + actualAssigneeId);
        }

        Instant now = clock.instant();

        Task task = new Task(
                TaskId.newId(),
                command.authorId(),
                actualAssigneeId,
                command.title(),
                command.description(),
                TaskStatus.OPEN,
                command.priority() == null ? TaskPriority.MEDIUM : command.priority(),
                command.dueAt(),
                now,
                now
        );

        return saveTaskPort.save(task);
    }
}