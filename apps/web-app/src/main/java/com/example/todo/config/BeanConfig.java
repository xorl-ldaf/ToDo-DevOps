package com.example.todo.config;

import com.example.todo.adapter.out.persistence.adapter.ReminderPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.TaskPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.UserPersistenceAdapter;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataTaskRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataUserRepository;
import com.example.todo.application.port.in.AssignTaskUseCase;
import com.example.todo.application.port.in.CreateReminderUseCase;
import com.example.todo.application.port.in.CreateTaskUseCase;
import com.example.todo.application.port.in.CreateUserUseCase;
import com.example.todo.application.port.in.GetTaskUseCase;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListTaskRemindersUseCase;
import com.example.todo.application.port.in.ListTasksUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import com.example.todo.application.port.out.PublishReminderEventPort;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import com.example.todo.application.service.AssignTaskService;
import com.example.todo.application.service.CreateReminderService;
import com.example.todo.application.service.CreateTaskService;
import com.example.todo.application.service.CreateUserService;
import com.example.todo.application.service.GetTaskService;
import com.example.todo.application.service.GetUserService;
import com.example.todo.application.service.ListTaskRemindersService;
import com.example.todo.application.service.ListTasksService;
import com.example.todo.application.service.ListUsersService;
import com.example.todo.application.service.ScanDueRemindersService;
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
    PublishReminderEventPort publishReminderEventPort() {
        return new NoOpReminderEventPublisher();
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
    CreateUserUseCase createUserUseCase(
            UserPersistenceAdapter userAdapter,
            Clock clock
    ) {
        return new CreateUserService(userAdapter, userAdapter, clock);
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
            ReminderPersistenceAdapter reminderAdapter,
            PublishReminderEventPort publishReminderEventPort
    ) {
        return new ScanDueRemindersService(
                reminderAdapter,
                publishReminderEventPort,
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
            PublishReminderScheduledEventPort publishReminderScheduledEventPort,
            Clock clock
    ) {
        return new CreateReminderService(taskAdapter, reminderAdapter, publishReminderScheduledEventPort, clock);
    }

    @Bean
    ListTaskRemindersUseCase listTaskRemindersUseCase(
            TaskPersistenceAdapter taskAdapter,
            ReminderPersistenceAdapter reminderAdapter
    ) {
        return new ListTaskRemindersService(taskAdapter, reminderAdapter);
    }
}
