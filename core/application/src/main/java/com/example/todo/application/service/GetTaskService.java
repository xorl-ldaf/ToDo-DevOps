package com.example.todo.application.service;

import com.example.todo.application.policy.TaskReferencePolicy;
import com.example.todo.application.port.in.GetTaskUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;

import java.util.Objects;
import java.util.Optional;

public class GetTaskService implements GetTaskUseCase {
    private final LoadTaskPort loadTaskPort;
    private final TaskReferencePolicy taskReferencePolicy;

    public GetTaskService(LoadTaskPort loadTaskPort) {
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
        this.taskReferencePolicy = new TaskReferencePolicy(loadTaskPort);
    }

    @Override
    public Optional<Task> getTask(TaskId taskId) {
        return loadTaskPort.loadById(taskReferencePolicy.requireTaskId(taskId));
    }

    @Override
    public Task getRequiredTask(TaskId taskId) {
        return taskReferencePolicy.requireTask(taskId);
    }
}
