package com.example.todo.application.service;

import com.example.todo.application.command.CreateTaskCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SaveTaskPort saveTaskPort;

    private CreateTaskService service;

    @BeforeEach
    void setUp() {
        service = new CreateTaskService(loadUserPort, saveTaskPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createTaskShouldSaveTaskWithDefaultsWhenAssigneeAndPriorityAreMissing() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        CreateTaskCommand command = new CreateTaskCommand(
                "Baseline task",
                null,
                authorId,
                null,
                null,
                null
        );
        when(loadUserPort.existsById(authorId)).thenReturn(true);
        when(saveTaskPort.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task createdTask = service.createTask(command);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        InOrder inOrder = inOrder(loadUserPort, saveTaskPort);
        inOrder.verify(loadUserPort).existsById(authorId);
        inOrder.verify(saveTaskPort).save(taskCaptor.capture());
        verifyNoMoreInteractions(loadUserPort, saveTaskPort);

        Task savedTask = taskCaptor.getValue();
        assertEquals(authorId, createdTask.getAuthorId());
        assertEquals(authorId, createdTask.getAssigneeId());
        assertEquals(TaskStatus.OPEN, createdTask.getStatus());
        assertEquals(TaskPriority.MEDIUM, createdTask.getPriority());
        assertEquals("", createdTask.getDescription());
        assertNull(createdTask.getDueAt());
        assertEquals(NOW, createdTask.getCreatedAt());
        assertEquals(NOW, createdTask.getUpdatedAt());

        assertEquals("Baseline task", savedTask.getTitle());
        assertEquals(authorId, savedTask.getAuthorId());
        assertEquals(authorId, savedTask.getAssigneeId());
        assertEquals(TaskPriority.MEDIUM, savedTask.getPriority());
        assertEquals(TaskStatus.OPEN, savedTask.getStatus());
    }

    @Test
    void createTaskShouldValidateAuthorIdBeforeCallingPorts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.createTask(new CreateTaskCommand("Task", "desc", null, null, null, null))
        );

        assertEquals("authorId must not be null", exception.getMessage());
        verifyNoInteractions(loadUserPort, saveTaskPort);
    }

    @Test
    void createTaskShouldRejectMissingAuthor() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        when(loadUserPort.existsById(authorId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.createTask(new CreateTaskCommand("Task", "desc", authorId, null, null, null))
        );

        assertEquals("author not found: " + authorId.value(), exception.getMessage());
        verify(loadUserPort).existsById(authorId);
        verifyNoMoreInteractions(loadUserPort);
        verifyNoInteractions(saveTaskPort);
    }

    @Test
    void createTaskShouldRejectMissingAssignee() {
        UserId authorId = userId("11111111-1111-1111-1111-111111111111");
        UserId assigneeId = userId("22222222-2222-2222-2222-222222222222");
        when(loadUserPort.existsById(authorId)).thenReturn(true);
        when(loadUserPort.existsById(assigneeId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.createTask(new CreateTaskCommand(
                        "Task",
                        "desc",
                        authorId,
                        assigneeId,
                        TaskPriority.HIGH,
                        NOW.plusSeconds(300)
                ))
        );

        assertEquals("assignee not found: " + assigneeId.value(), exception.getMessage());
        InOrder inOrder = inOrder(loadUserPort);
        inOrder.verify(loadUserPort).existsById(authorId);
        inOrder.verify(loadUserPort).existsById(assigneeId);
        verifyNoMoreInteractions(loadUserPort);
        verifyNoInteractions(saveTaskPort);
    }
    
    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
