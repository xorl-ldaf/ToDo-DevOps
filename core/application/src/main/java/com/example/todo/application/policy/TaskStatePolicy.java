package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;

import java.time.Instant;

public class TaskStatePolicy {

    public Task assign(Task task, UserId newAssigneeId, Instant now) {
        Task actualTask = requireNonNull(task, "task");

        Instant actualNow = requireValidUpdateTime(actualTask, now);
        try {
            return actualTask.assignTo(requireNonNull(newAssigneeId, "newAssigneeId"), actualNow);
        } catch (IllegalStateException ex) {
            throw new InvalidStateTransitionException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ApplicationValidationException(ex.getMessage());
        }
    }

    public Task markCompleted(Task task, Instant now) {
        Task actualTask = requireNonNull(task, "task");

        if (actualTask.status() != TaskStatus.OPEN && actualTask.status() != TaskStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "task cannot be completed from status: " + actualTask.status()
            );
        }

        return copyWith(
                actualTask,
                actualTask.assigneeId(),
                TaskStatus.DONE,
                requireValidUpdateTime(actualTask, now)
        );
    }

    private static Task copyWith(
            Task task,
            UserId assigneeId,
            TaskStatus status,
            Instant updatedAt
    ) {
        return Task.restore(
                task.id(),
                task.authorId(),
                assigneeId,
                task.title(),
                task.description(),
                status,
                task.priority(),
                task.dueAt(),
                task.createdAt(),
                updatedAt
        );
    }

    private static Instant requireValidUpdateTime(Task task, Instant now) {
        Instant actualNow = requireNonNull(now, "now");
        if (actualNow.isBefore(task.createdAt())) {
            throw new ApplicationValidationException("updatedAt must not be before createdAt");
        }
        if (actualNow.isBefore(task.updatedAt())) {
            throw new ApplicationValidationException("updatedAt must not move backwards");
        }
        return actualNow;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
