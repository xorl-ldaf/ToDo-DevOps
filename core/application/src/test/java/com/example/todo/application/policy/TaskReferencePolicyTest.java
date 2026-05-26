package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReferencePolicyTest {

    @Mock
    private LoadTaskPort loadTaskPort;

    private TaskReferencePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new TaskReferencePolicy(loadTaskPort);
    }

    @Test
    void requireTaskIdShouldRejectNullWithoutCallingPort() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> policy.requireTaskId(null)
        );

        assertEquals("taskId must not be null", exception.getMessage());
        verifyNoInteractions(loadTaskPort);
    }

    @Test
    void requireTaskShouldReturnLoadedTask() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Task task = task(taskId);
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task));

        Task result = policy.requireTask(taskId);

        assertSame(task, result);
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }

    @Test
    void requireTaskShouldThrowWhenTaskIsMissing() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> policy.requireTask(taskId)
        );

        assertEquals("task not found: " + taskId.value(), exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }

    @Test
    void requireTaskExistsShouldOnlyVerifyPresence() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task(taskId)));

        policy.requireTaskExists(taskId);

        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }

    private static Task task(TaskId taskId) {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        UserId userId = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return new Task(
                taskId,
                userId,
                userId,
                "Task",
                "desc",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                now,
                now
        );
    }

    private static TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }
}
