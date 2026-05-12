package com.example.todo.config;

import com.example.todo.adapter.out.persistence.adapter.ReminderPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.ReminderScheduledEventOutboxPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.ReminderScheduledEventReceiptPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.TaskPersistenceAdapter;
import com.example.todo.adapter.out.persistence.adapter.UserPersistenceAdapter;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderScheduledEventOutboxRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataReminderScheduledEventReceiptRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataTaskRepository;
import com.example.todo.adapter.out.persistence.repository.SpringDataUserRepository;
import com.example.todo.application.port.in.FlushReminderScheduledEventOutboxUseCase;
import com.example.todo.application.port.in.RecordReminderScheduledEventReceiptUseCase;
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
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import com.example.todo.application.port.out.StoreReminderScheduledEventPort;
import com.example.todo.application.service.AssignTaskService;
import com.example.todo.application.service.CreateReminderService;
import com.example.todo.application.service.CreateTaskService;
import com.example.todo.application.service.CreateUserService;
import com.example.todo.application.service.FlushReminderScheduledEventOutboxService;
import com.example.todo.application.service.GetTaskService;
import com.example.todo.application.service.GetUserService;
import com.example.todo.application.service.ListTaskRemindersService;
import com.example.todo.application.service.ListTasksService;
import com.example.todo.application.service.ListUsersService;
import com.example.todo.application.service.RecordReminderScheduledEventReceiptService;
import com.example.todo.application.service.ScanDueRemindersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.UUID;

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
    ReminderPersistenceAdapter reminderPersistenceAdapter(
            SpringDataReminderRepository repository
    ) {
        return new ReminderPersistenceAdapter(repository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    ReminderScheduledEventOutboxPersistenceAdapter reminderScheduledEventOutboxPersistenceAdapter(
            SpringDataReminderScheduledEventOutboxRepository repository,
            ObjectMapper objectMapper
    ) {
        return new ReminderScheduledEventOutboxPersistenceAdapter(repository, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    ReminderScheduledEventReceiptPersistenceAdapter reminderScheduledEventReceiptPersistenceAdapter(
            SpringDataReminderScheduledEventReceiptRepository repository
    ) {
        return new ReminderScheduledEventReceiptPersistenceAdapter(repository);
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
    ScanDueRemindersService scanDueRemindersService(
            ReminderPersistenceAdapter reminderAdapter,
            TaskPersistenceAdapter taskAdapter,
            UserPersistenceAdapter userAdapter,
            DeliverReminderNotificationPort deliverReminderNotificationPort,
            TodoReminderDeliveryProperties reminderDeliveryProperties
    ) {
        return new ScanDueRemindersService(
                reminderAdapter,
                taskAdapter,
                userAdapter,
                deliverReminderNotificationPort,
                reminderAdapter,
                processorId("reminder-delivery"),
                reminderDeliveryProperties.requireBatchSize(),
                reminderDeliveryProperties.requireMaxAttempts(),
                reminderDeliveryProperties.requireRetryBackoff(),
                reminderDeliveryProperties.requireProcessingTimeout()
        );
    }

    @Bean
    ScanDueRemindersUseCase scanDueRemindersUseCase(ScanDueRemindersService delegate) {
        return delegate;
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
            StoreReminderScheduledEventPort storeReminderScheduledEventPort,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        return new TransactionalCreateReminderUseCase(
                new CreateReminderService(taskAdapter, reminderAdapter, storeReminderScheduledEventPort, clock),
                transactionManager
        );
    }

    @Bean
    ListTaskRemindersUseCase listTaskRemindersUseCase(
            TaskPersistenceAdapter taskAdapter,
            ReminderPersistenceAdapter reminderAdapter
    ) {
        return new ListTaskRemindersService(taskAdapter, reminderAdapter);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    FlushReminderScheduledEventOutboxUseCase flushReminderScheduledEventOutboxUseCase(
            ReminderScheduledEventOutboxPersistenceAdapter outboxAdapter,
            PublishReminderScheduledEventPort publishReminderScheduledEventPort,
            TodoKafkaProperties kafkaProperties
    ) {
        return new FlushReminderScheduledEventOutboxService(
                outboxAdapter,
                outboxAdapter,
                publishReminderScheduledEventPort,
                processorId("kafka-outbox"),
                kafkaProperties.getOutbox().getBatchSize(),
                kafkaProperties.getOutbox().getMaxAttempts(),
                kafkaProperties.getOutbox().getRetryBackoff(),
                kafkaProperties.getOutbox().getProcessingTimeout()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    RecordReminderScheduledEventReceiptUseCase recordReminderScheduledEventReceiptUseCase(
            ReminderScheduledEventReceiptPersistenceAdapter receiptAdapter
    ) {
        return new RecordReminderScheduledEventReceiptService(receiptAdapter);
    }

    private String processorId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
