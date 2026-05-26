package com.example.todo.application.service;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTaskServiceTest {

    @Mock
    private LoadTaskPort loadTaskPort;

    private GetTaskService service;

    @BeforeEach
    void setUp() {
        service = new GetTaskService(loadTaskPort);
    }

    @Test
    void getTaskShouldDelegateToLoadPort() {
        TaskId taskId = new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        Task task = task(taskId);
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task));

        Optional<Task> result = service.getTask(taskId);

        assertEquals(Optional.of(task), result);
        assertSame(task, result.orElseThrow());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }

    @Test
    void getTaskShouldRejectNullTaskId() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.getTask(null)
        );

        assertEquals("taskId must not be null", exception.getMessage());
        verifyNoMoreInteractions(loadTaskPort);
    }

    @Test
    void getRequiredTaskShouldReturnTaskWhenFound() {
        TaskId taskId = new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        Task task = task(taskId);
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task));

        Task result = service.getRequiredTask(taskId);

        assertSame(task, result);
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }

    @Test
    void getRequiredTaskShouldThrowNotFoundWhenMissing() {
        TaskId taskId = new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getRequiredTask(taskId)
        );

        assertEquals("task not found: " + taskId.value(), exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }

    @Test
    void getRequiredTaskShouldRejectNullTaskId() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.getRequiredTask(null)
        );

        assertEquals("taskId must not be null", exception.getMessage());
        verifyNoMoreInteractions(loadTaskPort);
    }

    private static Task task(TaskId taskId) {
        return new Task(
                taskId,
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                "Task",
                "desc",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-20T10:00:00Z")
        );
    }
}
