package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReferencePolicyTest {

    @Mock
    private LoadUserPort loadUserPort;

    private UserReferencePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UserReferencePolicy(loadUserPort);
    }

    @Test
    void requireAuthorIdShouldRejectNullWithoutCallingPort() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.requireAuthorId(null)
        );

        assertEquals("authorId must not be null", exception.getMessage());
        verifyNoInteractions(loadUserPort);
    }

    @Test
    void requireAssigneeIdShouldRejectNullWithoutCallingPort() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.requireAssigneeId(null)
        );

        assertEquals("assigneeId must not be null", exception.getMessage());
        verifyNoInteractions(loadUserPort);
    }

    @Test
    void requireExistingAuthorShouldAcceptKnownUser() {
        UserId userId = userId("11111111-1111-1111-1111-111111111111");
        when(loadUserPort.existsById(userId)).thenReturn(true);

        policy.requireExistingAuthor(userId);

        assertSame(userId, policy.requireAuthorId(userId));
        verify(loadUserPort).existsById(userId);
        verifyNoMoreInteractions(loadUserPort);
    }

    @Test
    void requireExistingAuthorShouldThrowWhenUserIsMissing() {
        UserId userId = userId("11111111-1111-1111-1111-111111111111");
        when(loadUserPort.existsById(userId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> policy.requireExistingAuthor(userId)
        );

        assertEquals("author not found: " + userId.value(), exception.getMessage());
        verify(loadUserPort).existsById(userId);
        verifyNoMoreInteractions(loadUserPort);
    }

    @Test
    void requireExistingAssigneeIfPresentShouldSkipNullAssignee() {
        policy.requireExistingAssigneeIfPresent(null);

        verifyNoInteractions(loadUserPort);
    }

    @Test
    void requireExistingAssigneeShouldThrowWhenUserIsMissing() {
        UserId userId = userId("22222222-2222-2222-2222-222222222222");
        when(loadUserPort.existsById(userId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> policy.requireExistingAssignee(userId)
        );

        assertEquals("assignee not found: " + userId.value(), exception.getMessage());
        verify(loadUserPort).existsById(userId);
        verifyNoMoreInteractions(loadUserPort);
    }

    private static UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
