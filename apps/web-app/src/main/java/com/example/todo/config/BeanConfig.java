package com.example.todo.config;

import com.example.todo.adapter.out.persistence.adapter.ReminderPersistenceAdapter;

import com.example.todo.adapter.out.persistence.adapter.TaskPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.UserPersistenceAdapter;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataTaskRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataUserRepository;
import com.example.todo.application.port.in.*;
import com.example.todo.application.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeanConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    UserPersistenceAdapter userPersistenceAdapter(SpringDataUserRepository repository) {
        return new UserPersistenceAdapter(repository);
    }

    @Bean
    TaskPersistenceAdapter taskPersistenceAdapter(SpringDataTaskRepository repository) {
        return new TaskPersistenceAdapter(repository);
    }

    @Bean
    ReminderPersistenceAdapter reminderPersistenceAdapter(SpringDataReminderRepository repository) {
        return new ReminderPersistenceAdapter(repository);
    }

    @Bean
    CreateTaskUseCase createTaskUseCase(
            UserPersistenceAdapter userAdapter,
            TaskPersistenceAdapter taskAdapter,
            Clock clock
    ) {
        return new CreateTaskService(userAdapter, taskAdapter, clock);
    }

    @Bean
    AssignTaskUseCase assignTaskUseCase(
            TaskPersistenceAdapter taskAdapter,
            UserPersistenceAdapter userAdapter,
            Clock clock
    ) {
        return new AssignTaskService(taskAdapter, userAdapter, taskAdapter, clock);
    }

    @Bean
    ScanDueRemindersUseCase scanDueRemindersUseCase(
            ReminderPersistenceAdapter reminderAdapter
    ) {
        return new ScanDueRemindersService(
                reminderAdapter,
                reminder -> { },
                reminderAdapter
        );
    }

    @Bean
    GetTaskUseCase getTaskUseCase(TaskPersistenceAdapter taskAdapter) {
        return new GetTaskService(taskAdapter);
    }

    @Bean
    ListTasksUseCase listTasksUseCase(TaskPersistenceAdapter taskAdapter) {
        return new ListTasksService(taskAdapter);
    }

    @Bean
    GetUserUseCase getUserUseCase(UserPersistenceAdapter userAdapter) {
        return new GetUserService(userAdapter);
    }

    @Bean
    ListUsersUseCase listUsersUseCase(UserPersistenceAdapter userAdapter) {
        return new ListUsersService(userAdapter);
    }

    @Bean
    CreateReminderUseCase createReminderUseCase(
            TaskPersistenceAdapter taskAdapter,
            ReminderPersistenceAdapter reminderAdapter,
            Clock clock
    ) {
        return new CreateReminderService(taskAdapter, reminderAdapter, clock);
    }

    @Bean
    ListTaskRemindersUseCase listTaskRemindersUseCase(ReminderPersistenceAdapter reminderAdapter) {
        return new ListTaskRemindersService(reminderAdapter);
    }
}