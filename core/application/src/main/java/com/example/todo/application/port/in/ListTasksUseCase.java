package com.example.todo.application.port.in;

import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;

public interface ListTasksUseCase {
    PageResult<Task> listTasks(PageQuery pageQuery, TaskStatus status, TaskPriority priority);
}
