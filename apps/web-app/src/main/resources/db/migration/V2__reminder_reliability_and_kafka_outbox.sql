alter table reminders
    add column if not exists next_attempt_at timestamptz,
    add column if not exists processing_started_at timestamptz,
    add column if not exists processing_owner varchar(128),
    add column if not exists delivered_at timestamptz,
    add column if not exists delivery_attempts integer not null default 0,
    add column if not exists last_failure_reason varchar(512);

update reminders
set next_attempt_at = remind_at
where next_attempt_at is null;

update reminders
set status = case status
                 when 'PENDING' then 'SCHEDULED'
                 when 'PUBLISHED' then 'DELIVERED'
                 when 'SENT' then 'DELIVERED'
                 else status
    end;

update reminders
set delivered_at = coalesce(delivered_at, sent_at, updated_at)
where status = 'DELIVERED'
  and delivered_at is null;

update reminders
set last_failure_reason = coalesce(last_failure_reason, 'legacy failure before failure reason tracking')
where status = 'FAILED'
  and last_failure_reason is null;

alter table reminders
    drop constraint if exists chk_reminders_status,
    drop constraint if exists chk_reminders_sent_at,
    add constraint chk_reminders_status check (status in ('SCHEDULED', 'PROCESSING', 'DELIVERED', 'FAILED')),
    add constraint chk_reminders_next_attempt_at check (next_attempt_at >= created_at),
    add constraint chk_reminders_processing_started_at check (
        processing_started_at is null or processing_started_at >= created_at
    ),
    add constraint chk_reminders_delivered_at check (
        delivered_at is null or delivered_at >= created_at
    ),
    add constraint chk_reminders_delivery_attempts check (delivery_attempts >= 0),
    add constraint chk_reminders_processing_state check (
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
    add constraint chk_reminders_delivered_state check (
        (
            status = 'DELIVERED'
            and delivered_at is not null
        ) or (
            status <> 'DELIVERED'
            and delivered_at is null
        )
    ),
    add constraint chk_reminders_failed_state check (
        status <> 'FAILED' or last_failure_reason is not null
    );

alter table reminders
    alter column next_attempt_at set not null;

drop index if exists idx_reminders_status_remind_at;
create index if not exists idx_reminders_status_next_attempt_at on reminders(status, next_attempt_at);

alter table reminders
    drop column if exists sent_at;

create table if not exists reminder_scheduled_event_outbox (
    event_id uuid primary key,
    reminder_id uuid not null references reminders(id) on delete cascade,
    task_id uuid not null references tasks(id) on delete cascade,
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
    constraint chk_reminder_scheduled_event_outbox_status check (status in ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
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
    )
);

create index if not exists idx_reminder_scheduled_event_outbox_status_available_at
    on reminder_scheduled_event_outbox(status, available_at);

create table if not exists reminder_scheduled_event_receipts (
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
);

create index if not exists idx_reminder_scheduled_event_receipts_consumed_at
    on reminder_scheduled_event_receipts(consumed_at);

create unique index if not exists uq_reminder_scheduled_event_receipts_topic_partition_offset
    on reminder_scheduled_event_receipts(topic, kafka_partition, kafka_offset);
