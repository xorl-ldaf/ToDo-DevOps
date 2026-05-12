package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.adapter.ReminderPersistenceAdapter;
import com.example.todo.adapter.out.persistence.entity.ReminderJpaEntity;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@SpringJUnitConfig(classes = AbstractReminderPersistenceRepositoryIT.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractReminderPersistenceRepositoryIT {
    protected static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected SpringDataReminderRepository repository;

    @Autowired
    protected SpringDataReminderScheduledEventOutboxRepository outboxRepository;

    @Autowired
    protected SpringDataReminderScheduledEventReceiptRepository receiptRepository;

    @Autowired
    protected ReminderPersistenceAdapter adapter;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("drop table if exists reminder_scheduled_event_outbox");
        jdbcTemplate.execute("drop table if exists reminder_scheduled_event_receipts");
        jdbcTemplate.execute("drop table if exists reminders");
        jdbcTemplate.execute("drop table if exists tasks");
        jdbcTemplate.execute("drop table if exists users");
        jdbcTemplate.execute("""
                create table users (
                    id uuid primary key,
                    username varchar(100) not null,
                    display_name varchar(150) not null,
                    telegram_chat_id bigint null,
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    constraint chk_users_updated_at check (updated_at >= created_at)
                )
                """);
        jdbcTemplate.execute("create unique index uq_users_username_lower on users (lower(username))");
        jdbcTemplate.execute("""
                create table tasks (
                    id uuid primary key,
                    author_id uuid not null,
                    assignee_id uuid not null,
                    title varchar(200) not null,
                    description text not null default '',
                    status varchar(32) not null,
                    priority varchar(32) not null,
                    due_at timestamptz null,
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    constraint fk_tasks_author foreign key (author_id) references users(id),
                    constraint fk_tasks_assignee foreign key (assignee_id) references users(id),
                    constraint chk_tasks_status check (status in ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
                    constraint chk_tasks_priority check (priority in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
                    constraint chk_tasks_updated_at check (updated_at >= created_at)
                )
                """);
        jdbcTemplate.execute("create index idx_tasks_author_id on tasks(author_id)");
        jdbcTemplate.execute("create index idx_tasks_assignee_id on tasks(assignee_id)");
        jdbcTemplate.execute("create index idx_tasks_status on tasks(status)");
        jdbcTemplate.execute("""
                create table reminders (
                    id uuid primary key,
                    task_id uuid not null,
                    remind_at timestamptz not null,
                    status varchar(32) not null,
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    next_attempt_at timestamptz not null,
                    processing_started_at timestamptz null,
                    processing_owner varchar(128) null,
                    delivered_at timestamptz null,
                    delivery_attempts integer not null default 0,
                    last_failure_reason varchar(512) null,
                    constraint fk_reminders_task foreign key (task_id) references tasks(id) on delete cascade,
                    constraint chk_reminders_status check (status in ('SCHEDULED', 'PROCESSING', 'DELIVERED', 'FAILED')),
                    constraint chk_reminders_updated_at check (updated_at >= created_at),
                    constraint chk_reminders_next_attempt_at check (next_attempt_at >= created_at),
                    constraint chk_reminders_processing_started_at check (
                        processing_started_at is null or processing_started_at >= created_at
                    ),
                    constraint chk_reminders_delivered_at check (delivered_at is null or delivered_at >= created_at),
                    constraint chk_reminders_delivery_attempts check (delivery_attempts >= 0),
                    constraint chk_reminders_processing_state check (
                        (
                            status = 'PROCESSING'
                            and processing_started_at is not null
                            and processing_owner is not null
                        ) or (
                            status <> 'PROCESSING'
                            and processing_started_at is null
                            and processing_owner is null
                        )
                    ),
                    constraint chk_reminders_delivered_state check (
                        (
                            status = 'DELIVERED'
                            and delivered_at is not null
                        ) or (
                            status <> 'DELIVERED'
                            and delivered_at is null
                        )
                    ),
                    constraint chk_reminders_failed_state check (
                        status <> 'FAILED' or last_failure_reason is not null
                    )
                )
                """);
        jdbcTemplate.execute("create index idx_reminders_task_id on reminders(task_id)");
        jdbcTemplate.execute("create index idx_reminders_status_next_attempt_at on reminders(status, next_attempt_at)");
        jdbcTemplate.execute("create index idx_reminders_processing_started_at on reminders(processing_started_at)");
        jdbcTemplate.execute("""
                create table reminder_scheduled_event_outbox (
                    event_id uuid primary key,
                    reminder_id uuid not null references reminders(id) on delete cascade,
                    task_id uuid not null references tasks(id) on delete cascade,
                    event_type varchar(128) not null,
                    event_version varchar(32) not null,
                    payload text not null,
                    status varchar(32) not null,
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    available_at timestamptz not null,
                    processing_started_at timestamptz null,
                    processing_owner varchar(128) null,
                    published_at timestamptz null,
                    delivery_attempts integer not null default 0,
                    last_failure_reason varchar(512) null,
                    constraint chk_reminder_scheduled_event_outbox_status check (
                        status in ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')
                    ),
                    constraint chk_reminder_scheduled_event_outbox_updated_at check (updated_at >= created_at),
                    constraint chk_reminder_scheduled_event_outbox_available_at check (available_at >= created_at),
                    constraint chk_reminder_scheduled_event_outbox_processing_started_at check (
                        processing_started_at is null or processing_started_at >= created_at
                    ),
                    constraint chk_reminder_scheduled_event_outbox_published_at check (
                        published_at is null or published_at >= created_at
                    ),
                    constraint chk_reminder_scheduled_event_outbox_delivery_attempts check (delivery_attempts >= 0),
                    constraint chk_reminder_scheduled_event_outbox_processing_state check (
                        (
                            status = 'PROCESSING'
                            and processing_started_at is not null
                            and processing_owner is not null
                        ) or (
                            status <> 'PROCESSING'
                            and processing_started_at is null
                            and processing_owner is null
                        )
                    ),
                    constraint chk_reminder_scheduled_event_outbox_published_state check (
                        (
                            status = 'PUBLISHED'
                            and published_at is not null
                        ) or (
                            status <> 'PUBLISHED'
                            and published_at is null
                        )
                    ),
                    constraint chk_reminder_scheduled_event_outbox_failed_state check (
                        status <> 'FAILED' or last_failure_reason is not null
                    ),
                    constraint chk_reminder_scheduled_event_outbox_event_type check (length(trim(event_type)) > 0),
                    constraint chk_reminder_scheduled_event_outbox_event_version check (length(trim(event_version)) > 0)
                )
                """);
        jdbcTemplate.execute("""
                create index idx_reminder_scheduled_event_outbox_status_available_at
                on reminder_scheduled_event_outbox(status, available_at)
                """);
        jdbcTemplate.execute("""
                create table reminder_scheduled_event_receipts (
                    event_id uuid primary key,
                    reminder_id uuid not null references reminders(id) on delete cascade,
                    task_id uuid not null references tasks(id) on delete cascade,
                    topic varchar(255) not null,
                    event_version varchar(32) not null,
                    occurred_at timestamptz not null,
                    consumed_at timestamptz not null,
                    kafka_partition integer not null,
                    kafka_offset bigint not null,
                    payload text not null,
                    constraint chk_reminder_scheduled_event_receipts_consumed_at check (consumed_at >= occurred_at)
                )
                """);
        jdbcTemplate.execute("""
                create index idx_reminder_scheduled_event_receipts_consumed_at
                on reminder_scheduled_event_receipts(consumed_at)
                """);
        jdbcTemplate.execute("""
                create unique index uq_reminder_scheduled_event_receipts_topic_partition_offset
                on reminder_scheduled_event_receipts(topic, kafka_partition, kafka_offset)
                """);
    }

    protected void seedTask(UUID taskId) {
        UUID userId = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(NOW.minusSeconds(3600));
        jdbcTemplate.update(
                """
                        insert into users (id, username, display_name, telegram_chat_id, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "user-" + userId,
                "Test User",
                123456789L,
                timestamp,
                timestamp
        );
        jdbcTemplate.update(
                """
                        insert into tasks (
                            id, author_id, assignee_id, title, description, status, priority, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                taskId,
                userId,
                userId,
                "Task",
                "Description",
                "OPEN",
                "MEDIUM",
                timestamp,
                timestamp
        );
    }

    protected Reminder scheduledReminder(UUID reminderId, UUID taskId, Instant nextAttemptAt) {
        Instant createdAt = NOW.minusSeconds(300);
        return Reminder.restore(
                new ReminderId(reminderId),
                new TaskId(taskId),
                NOW.minusSeconds(60),
                ReminderStatus.SCHEDULED,
                createdAt,
                createdAt,
                nextAttemptAt,
                null,
                null,
                null,
                0,
                null
        );
    }

    protected Reminder processingReminder(
            UUID reminderId,
            UUID taskId,
            String owner,
            Instant processingStartedAt,
            int deliveryAttempts
    ) {
        Instant createdAt = NOW.minusSeconds(300);
        return Reminder.restore(
                new ReminderId(reminderId),
                new TaskId(taskId),
                NOW.minusSeconds(60),
                ReminderStatus.PROCESSING,
                createdAt,
                processingStartedAt,
                NOW.minusSeconds(60),
                processingStartedAt,
                owner,
                null,
                deliveryAttempts,
                "previous timeout"
        );
    }

    protected ReminderJpaEntity requireReminder(UUID reminderId) {
        return repository.findById(reminderId).orElseThrow();
    }

    @Configuration
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackageClasses = ReminderJpaEntity.class)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackageClasses = SpringDataReminderRepository.class)
    static class TestConfig {
        @Bean
        ReminderPersistenceAdapter reminderPersistenceAdapter(SpringDataReminderRepository repository) {
            return new ReminderPersistenceAdapter(repository);
        }
    }
}
