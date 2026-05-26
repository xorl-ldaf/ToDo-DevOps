package com.example.todo.application.service;

import com.example.todo.application.policy.TaskReferencePolicy;
import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.task.TaskId;

import java.util.List;
import java.util.Objects;

public class ListTaskRemindersService implements ListTaskRemindersUseCase {
    private final TaskReferencePolicy taskReferencePolicy;
    private final LoadTaskRemindersPort loadTaskRemindersPort;

    public ListTaskRemindersService(
            LoadTaskPort loadTaskPort,
            LoadTaskRemindersPort loadTaskRemindersPort
    ) {
        this.taskReferencePolicy = new TaskReferencePolicy(loadTaskPort);
        this.loadTaskRemindersPort = Objects.requireNonNull(loadTaskRemindersPort, "loadTaskRemindersPort must not be null");
    }

    @Override
    public List<Reminder> listTaskReminders(TaskId taskId) {
        taskReferencePolicy.requireTaskExists(taskId);
        return loadTaskRemindersPort.loadByTaskId(taskId);
    }
}
