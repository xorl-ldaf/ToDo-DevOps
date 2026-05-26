package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.domain.user.UserId;

import java.util.Objects;

public class UserReferencePolicy {
    private final LoadUserPort loadUserPort;

    public UserReferencePolicy(LoadUserPort loadUserPort) {
        this.loadUserPort = Objects.requireNonNull(loadUserPort, "loadUserPort must not be null");
    }

    public UserId requireAuthorId(UserId authorId) {
        if (authorId == null) {
            throw new ApplicationValidationException("authorId must not be null");
        }
        return authorId;
    }

    public UserId requireAssigneeId(UserId assigneeId) {
        if (assigneeId == null) {
            throw new ApplicationValidationException("assigneeId must not be null");
        }
        return assigneeId;
    }

    public void requireExistingAuthor(UserId authorId) {
        UserId actualAuthorId = requireAuthorId(authorId);
        if (!loadUserPort.existsById(actualAuthorId)) {
            throw new ResourceNotFoundException("author not found: " + actualAuthorId.value());
        }
    }

    public void requireExistingAssignee(UserId assigneeId) {
        UserId actualAssigneeId = requireAssigneeId(assigneeId);
        if (!loadUserPort.existsById(actualAssigneeId)) {
            throw new ResourceNotFoundException("assignee not found: " + actualAssigneeId.value());
        }
    }

    public void requireExistingAssigneeIfPresent(UserId assigneeId) {
        if (assigneeId != null) {
            requireExistingAssignee(assigneeId);
        }
    }
}
