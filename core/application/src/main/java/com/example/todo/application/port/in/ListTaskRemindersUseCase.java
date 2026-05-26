package com.example.todo.application.port.in;

import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;

public interface ListTaskRemindersUseCase {
    PageResult<Reminder> listTaskReminders(TaskId taskId, PageQuery pageQuery, ReminderStatus status);
}
