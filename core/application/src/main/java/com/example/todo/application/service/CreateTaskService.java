package com.example.todo.application.service;

import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.factory.TaskFactory;
import com.example.todo.application.policy.UserReferencePolicy;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;

import java.time.Clock;
import java.util.Objects;

public class CreateTaskService implements CreateTaskUseCase {
    private final UserReferencePolicy userReferencePolicy;
    private final SaveTaskPort saveTaskPort;
    private final Clock clock;
    private final TaskFactory taskFactory;

    public CreateTaskService(
            LoadUserPort loadUserPort,
            SaveTaskPort saveTaskPort,
            Clock clock
    ) {
        this(loadUserPort, saveTaskPort, clock, new TaskFactory());
    }

    public CreateTaskService(
            LoadUserPort loadUserPort,
            SaveTaskPort saveTaskPort,
            Clock clock,
            TaskFactory taskFactory
    ) {
        this(new UserReferencePolicy(loadUserPort), saveTaskPort, clock, taskFactory);
    }

    public CreateTaskService(
            UserReferencePolicy userReferencePolicy,
            SaveTaskPort saveTaskPort,
            Clock clock,
            TaskFactory taskFactory
    ) {
        this.userReferencePolicy = Objects.requireNonNull(
                userReferencePolicy,
                "userReferencePolicy must not be null"
        );
        this.saveTaskPort = Objects.requireNonNull(saveTaskPort, "saveTaskPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.taskFactory = Objects.requireNonNull(taskFactory, "taskFactory must not be null");
    }

    @Override
    public Task createTask(CreateTaskCommand command) {
        if (command == null) {
            throw new ApplicationValidationException("command must not be null");
        }

        Task task = taskFactory.create(
                command.authorId(),
                command.assigneeId(),
                command.title(),
                command.description(),
                command.priority(),
                command.dueAt(),
                clock.instant()
        );

        userReferencePolicy.requireExistingAuthor(task.getAuthorId());
        userReferencePolicy.requireExistingAssigneeIfPresent(command.assigneeId());

        return saveTaskPort.save(task);
    }
}
