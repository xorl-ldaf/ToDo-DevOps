package com.example.todo.application.service;

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
        Task task = Task.restore(
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
        when(loadTaskPort.loadById(taskId)).thenReturn(Optional.of(task));

        Optional<Task> result = service.getTask(taskId);

        assertEquals(Optional.of(task), result);
        assertSame(task, result.orElseThrow());
        verify(loadTaskPort).loadById(taskId);
        verifyNoMoreInteractions(loadTaskPort);
    }
}
