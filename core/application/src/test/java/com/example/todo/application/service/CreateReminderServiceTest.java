package com.example.todo.application.service;

import com.example.todo.application.command.CreateReminderCommand;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.shared.exception.DomainValidationException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private SaveReminderPort saveReminderPort;

    private CreateReminderService service;

    @BeforeEach
    void setUp() {
        service = new CreateReminderService(loadTaskPort, saveReminderPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createReminderShouldScheduleAndSaveReminderForExistingTask() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant remindAt = NOW.plusSeconds(3600);
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task(taskId)));
        when(saveReminderPort.save(any(Reminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reminder createdReminder = service.createReminder(new CreateReminderCommand(taskId, remindAt));

        ArgumentCaptor<Reminder> reminderCaptor = ArgumentCaptor.forClass(Reminder.class);
        InOrder inOrder = inOrder(loadTaskPort, saveReminderPort);
        inOrder.verify(loadTaskPort).loadById(taskId);
        inOrder.verify(saveReminderPort).save(reminderCaptor.capture());
        verifyNoMoreInteractions(loadTaskPort, saveReminderPort);

        Reminder savedReminder = reminderCaptor.getValue();
        assertEquals(taskId, createdReminder.getTaskId());
        assertEquals(remindAt, createdReminder.getRemindAt());
        assertEquals(ReminderStatus.PENDING, createdReminder.getStatus());
        assertEquals(NOW, createdReminder.getCreatedAt());
        assertEquals(NOW, createdReminder.getUpdatedAt());
        assertNull(createdReminder.getSentAt());

        assertEquals(taskId, savedReminder.getTaskId());
        assertEquals(remindAt, savedReminder.getRemindAt());
        assertEquals(ReminderStatus.PENDING, savedReminder.getStatus());
    }

    @Test
    void createReminderShouldValidateTaskIdBeforeCallingPorts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.createReminder(new CreateReminderCommand(null, NOW.plusSeconds(60)))
        );

        assertEquals("taskId must not be null", exception.getMessage());
        verifyNoInteractions(loadTaskPort, saveReminderPort);
    }

    @Test
    void createReminderShouldValidateRemindAtBeforeCallingPorts() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.createReminder(new CreateReminderCommand(taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), null))
        );

        assertEquals("remindAt must not be null", exception.getMessage());
        verifyNoInteractions(loadTaskPort, saveReminderPort);
    }

    @Test
    void createReminderShouldRejectMissingTask() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.createReminder(new CreateReminderCommand(taskId, NOW.plusSeconds(60)))
        );

        assertEquals("task not found: " + taskId.value(), exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
        verifyNoInteractions(saveReminderPort);
    }

    @Test
    void createReminderShouldPropagateDomainValidationFailuresWithoutSaving() {
        TaskId taskId = taskId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task(taskId)));

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> service.createReminder(new CreateReminderCommand(taskId, NOW.minusSeconds(1)))
        );

        assertEquals("remindAt must not be in the past", exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
        verifyNoInteractions(saveReminderPort);
    }

    private Task task(TaskId taskId) {
        return Task.restore(
                taskId,
                userId("11111111-1111-1111-1111-111111111111"),
                userId("11111111-1111-1111-1111-111111111111"),
                "Task",
                "desc",
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
