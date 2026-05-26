package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;

import java.util.Objects;

public class TaskReferencePolicy {
    private final LoadTaskPort loadTaskPort;

    public TaskReferencePolicy(LoadTaskPort loadTaskPort) {
        this.loadTaskPort = Objects.requireNonNull(loadTaskPort, "loadTaskPort must not be null");
    }

    public TaskId requireTaskId(TaskId taskId) {
        if (taskId == null) {
            throw new ApplicationValidationException("taskId must not be null");
        }
        return taskId;
    }

    public Task requireTask(TaskId taskId) {
        TaskId actualTaskId = requireTaskId(taskId);
        return loadTaskPort.loadById(actualTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("task not found: " + actualTaskId.value()));
    }

    public void requireTaskExists(TaskId taskId) {
        TaskId actualTaskId = requireTaskId(taskId);
        if (loadTaskPort.loadById(actualTaskId).isEmpty()) {
            throw new ResourceNotFoundException("task not found: " + actualTaskId.value());
        }
    }
}
