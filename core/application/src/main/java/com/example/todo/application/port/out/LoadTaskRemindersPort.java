package com.example.todo.application.port.out;

import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;

public interface LoadTaskRemindersPort {
    PageResult<Reminder> loadByTaskId(TaskId taskId, PageQuery pageQuery, ReminderStatus status);
}
