package com.example.todo.application.service;

import com.example.todo.application.policy.TaskReferencePolicy;
import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadTaskRemindersPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;

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
    public PageResult<Reminder> listTaskReminders(TaskId taskId, PageQuery pageQuery, ReminderStatus status) {
        taskReferencePolicy.requireTaskExists(taskId);
        return loadTaskRemindersPort.loadByTaskId(
                taskId,
                Objects.requireNonNull(pageQuery, "pageQuery must not be null"),
                status
        );
    }
}
