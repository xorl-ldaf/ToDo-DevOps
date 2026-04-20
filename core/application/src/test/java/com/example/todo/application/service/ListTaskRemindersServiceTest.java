package com.example.todo.application.service;

import com.example.todo.application.exception.ResourceNotFoundException;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListTaskRemindersServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private LoadTaskRemindersPort loadTaskRemindersPort;

    private ListTaskRemindersService service;

    @BeforeEach
    void setUp() {
        service = new ListTaskRemindersService(loadTaskPort, loadTaskRemindersPort);
    }

    @Test
    void listTaskRemindersShouldLoadRemindersForExistingTask() {
        TaskId taskId = new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        Reminder reminder = Reminder.restore(
                new ReminderId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                taskId,
                NOW.plusSeconds(300),
                ReminderStatus.PENDING,
                NOW,
                NOW,
                null
        );
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task(taskId)));
        when(loadTaskRemindersPort.loadByTaskId(taskId)).thenReturn(List.of(reminder));

        List<Reminder> result = service.listTaskReminders(taskId);

        assertEquals(List.of(reminder), result);
        InOrder inOrder = inOrder(loadTaskPort, loadTaskRemindersPort);
        inOrder.verify(loadTaskPort).loadById(taskId);
        inOrder.verify(loadTaskRemindersPort).loadByTaskId(taskId);
        verifyNoMoreInteractions(loadTaskPort, loadTaskRemindersPort);
    }

    @Test
    void listTaskRemindersShouldRejectMissingTaskWithoutLoadingReminders() {
        TaskId taskId = new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.listTaskReminders(taskId)
        );

        assertEquals("task not found: " + taskId.value(), exception.getMessage());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
        verifyNoInteractions(loadTaskRemindersPort);
    }

    private Task task(TaskId taskId) {
        return Task.restore(
                taskId,
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                "Task",
                "desc",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                NOW.minusSeconds(300),
                NOW.minusSeconds(300)
        );
    }
}
