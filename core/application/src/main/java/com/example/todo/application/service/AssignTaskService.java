package com.example.todo.application.service;

import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.policy.TaskReferencePolicy;
import com.example.todo.application.policy.TaskStatePolicy;
import com.example.todo.application.policy.UserReferencePolicy;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.user.UserId;

import java.time.Clock;
import java.util.Objects;

public class AssignTaskService implements AssignTaskUseCase {
    private final TaskReferencePolicy taskReferencePolicy;
    private final UserReferencePolicy userReferencePolicy;
    private final SaveTaskPort saveTaskPort;
    private final Clock clock;
    private final TaskStatePolicy taskStatePolicy;

    public AssignTaskService(
            LoadTaskPort loadTaskPort,
            LoadUserPort loadUserPort,
            SaveTaskPort saveTaskPort,
            Clock clock
    ) {
        this(loadTaskPort, loadUserPort, saveTaskPort, clock, new TaskStatePolicy());
    }

    public AssignTaskService(
            LoadTaskPort loadTaskPort,
            LoadUserPort loadUserPort,
            SaveTaskPort saveTaskPort,
            Clock clock,
            TaskStatePolicy taskStatePolicy
    ) {
        this(
                new TaskReferencePolicy(loadTaskPort),
                new UserReferencePolicy(loadUserPort),
                saveTaskPort,
                clock,
                taskStatePolicy
        );
    }

    public AssignTaskService(
            TaskReferencePolicy taskReferencePolicy,
            UserReferencePolicy userReferencePolicy,
            SaveTaskPort saveTaskPort,
            Clock clock,
            TaskStatePolicy taskStatePolicy
    ) {
        this.taskReferencePolicy = Objects.requireNonNull(
                taskReferencePolicy,
                "taskReferencePolicy must not be null"
        );
        this.userReferencePolicy = Objects.requireNonNull(
                userReferencePolicy,
                "userReferencePolicy must not be null"
        );
        this.saveTaskPort = Objects.requireNonNull(saveTaskPort, "saveTaskPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.taskStatePolicy = Objects.requireNonNull(
                taskStatePolicy,
                "taskStatePolicy must not be null"
        );
    }

    @Override
    public Task assignTask(AssignTaskCommand command) {
        if (command == null) {
            throw new ApplicationValidationException("command must not be null");
        }

        TaskId taskId = taskReferencePolicy.requireTaskId(command.taskId());
        UserId assigneeId = userReferencePolicy.requireAssigneeId(command.assigneeId());
        Task task = taskReferencePolicy.requireTask(taskId);
        userReferencePolicy.requireExistingAssignee(assigneeId);

        Task assignedTask = taskStatePolicy.assign(task, assigneeId, clock.instant());

        return saveTaskPort.save(assignedTask);
    }
}
