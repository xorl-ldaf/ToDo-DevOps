package com.example.todo.application.service;

import com.example.todo.application.port.out.LoadAllTasksPort;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTasksServiceTest {

    @Mock
    private LoadAllTasksPort loadAllTasksPort;

    private ListTasksService service;

    @BeforeEach
    void setUp() {
        service = new ListTasksService(loadAllTasksPort);
    }

    @Test
    void listTasksShouldDelegateToLoadPort() {
        Task firstTask = task("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "First");
        Task secondTask = task("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "Second");
        when(loadAllTasksPort.loadAll()).thenReturn(List.of(firstTask, secondTask));

        List<Task> result = service.listTasks();

        assertEquals(List.of(firstTask, secondTask), result);
        verify(loadAllTasksPort).loadAll();
        verifyNoMoreInteractions(loadAllTasksPort);
    }

    private Task task(String taskId, String title) {
        return Task.restore(
                new TaskId(UUID.fromString(taskId)),
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                title,
                "desc",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-20T10:00:00Z")
        );
    }
}
