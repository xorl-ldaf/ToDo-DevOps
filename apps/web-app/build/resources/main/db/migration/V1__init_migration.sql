create table users (
                       id uuid primary key,
                       username varchar(100) not null,
                       display_name varchar(150) not null,
                       telegram_chat_id bigint null,
                       created_at timestamptz not null,
                       updated_at timestamptz not null,
                       constraint chk_users_updated_at check (updated_at >= created_at)
);

create unique index uq_users_username_lower on users (lower(username));

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
);

create index idx_tasks_author_id on tasks(author_id);
create index idx_tasks_assignee_id on tasks(assignee_id);
create index idx_tasks_status on tasks(status);

create table reminders (
                           id uuid primary key,
                           task_id uuid not null,
                           remind_at timestamptz not null,
                           status varchar(32) not null,
                           created_at timestamptz not null,
                           updated_at timestamptz not null,
                           sent_at timestamptz null,
                           constraint fk_reminders_task foreign key (task_id) references tasks(id) on delete cascade,
                           constraint chk_reminders_status check (status in ('PENDING', 'PUBLISHED', 'SENT', 'FAILED')),
                           constraint chk_reminders_updated_at check (updated_at >= created_at),
                           constraint chk_reminders_sent_at check (sent_at is null or sent_at >= created_at)
);

create index idx_reminders_task_id on reminders(task_id);
create index idx_reminders_status_remind_at on reminders(status, remind_at);