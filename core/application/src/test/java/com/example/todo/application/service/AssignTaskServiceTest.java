package com.example.todo.application.service;

import com.example.todo.application.command.AssignTaskCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T11:00:00Z");

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SaveTaskPort saveTaskPort;

    private AssignTaskService service;

    @BeforeEach
    void setUp() {
        service = new AssignTaskService(loadTaskPort, loadUserPort, saveTaskPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void assignTaskShouldReassignTaskAndPersistUpdatedAggregate() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UserId initialAssigneeId = userId("22222222-2222-2222-2222-222222222222");
        UserId newAssigneeId = userId("33333333-3333-3333-3333-333333333333");
        Task task = Task.restore(
                taskId,
                userId("11111111-1111-1111-1111-111111111111"),
                initialAssigneeId,
                "Assign baseline",
                "task",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                NOW.minusSeconds(300),
                NOW.minusSeconds(300)
        );
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task));
        when(loadUserPort.existsById(newAssigneeId)).thenReturn(true);
        when(saveTaskPort.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task assignedTask = service.assignTask(new AssignTaskCommand(taskId, newAssigneeId));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        InOrder inOrder = inOrder(loadTaskPort, loadUserPort, saveTaskPort);
        inOrder.verify(loadTaskPort).loadById(taskId);
        inOrder.verify(loadUserPort).existsById(newAssigneeId);
        inOrder.verify(saveTaskPort).save(taskCaptor.capture());
        verifyNoMoreInteractions(loadTaskPort, loadUserPort, saveTaskPort);

        Task savedTask = taskCaptor.getValue();
        assertEquals(newAssigneeId, assignedTask.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, assignedTask.getStatus());
        assertEquals(NOW, assignedTask.getUpdatedAt());

        assertEquals(newAssigneeId, savedTask.getAssigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, savedTask.getStatus());
        assertEquals(NOW, savedTask.getUpdatedAt());
    }

    @Test
    void assignTaskShouldValidateTaskIdBeforeCallingPorts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.assignTask(new AssignTaskCommand(null, userId("33333333-3333-3333-3333-333333333333")))
        );

        assertEquals("taskId must not be null", exception.getMessage());
        verifyNoInteractions(loadTaskPort, loadUserPort, saveTaskPort);
    }

    @Test
    void assignTaskShouldValidateAssigneeIdBeforeCallingPorts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.assignTask(new AssignTaskCommand(taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), null))
        );

        assertEquals("assigneeId must not be null", exception.getMessage());
        verifyNoInteractions(loadTaskPort, loadUserPort, saveTaskPort);
    }

    @Test
    void assignTaskShouldRejectMissingTask() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.assignTask(new AssignTaskCommand(taskId, userId("33333333-3333-3333-3333-333333333333")))
        );

        assertEquals("task not found: " + taskId.value(), exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
        verifyNoInteractions(loadUserPort, saveTaskPort);
    }

    @Test
    void assignTaskShouldRejectMissingAssignee() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UserId assigneeId = userId("33333333-3333-3333-3333-333333333333");
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(openTask(taskId)));
        when(loadUserPort.existsById(assigneeId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.assignTask(new AssignTaskCommand(taskId, assigneeId))
        );

        assertEquals("assignee not found: " + assigneeId.value(), exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verify(loadUserPort).existsById(assigneeId);
        verifyNoMoreInteractions(loadTaskPort, loadUserPort);
        verifyNoInteractions(saveTaskPort);
    }

    @Test
    void assignTaskShouldPropagateIllegalTransitionWithoutSaving() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UserId assigneeId = userId("33333333-3333-3333-3333-333333333333");
        Task doneTask = Task.restore(
                taskId,
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Done task",
                "no reassignment",
                TaskStatus.DONE,
                TaskPriority.HIGH,
                null,
                NOW.minusSeconds(300),
                NOW.minusSeconds(300)
        );
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(doneTask));
        when(loadUserPort.existsById(assigneeId)).thenReturn(true);

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> service.assignTask(new AssignTaskCommand(taskId, assigneeId))
        );

        assertEquals("task cannot be reassigned from status: DONE", exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verify(loadUserPort).existsById(assigneeId);
        verifyNoMoreInteractions(loadTaskPort, loadUserPort);
        verifyNoInteractions(saveTaskPort);
    }

    private Task openTask(TaskId taskId) {
        return Task.restore(
                taskId,
                userId("11111111-1111-1111-1111-111111111111"),
                userId("22222222-2222-2222-2222-222222222222"),
                "Open task",
                "assign",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                NOW.minusSeconds(300),
                NOW.minusSeconds(300)
        );
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
