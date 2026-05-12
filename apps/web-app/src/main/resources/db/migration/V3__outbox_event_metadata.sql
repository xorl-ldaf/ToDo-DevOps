alter table reminder_scheduled_event_outbox
    add column if not exists event_type varchar(128),
    add column if not exists event_version varchar(32);

update reminder_scheduled_event_outbox
set event_type = 'reminder.scheduled'
where event_type is null;

update reminder_scheduled_event_outbox
set event_version = 'v1'
where event_version is null;

update reminder_scheduled_event_outbox
set payload = (
    payload::jsonb - 'status' || jsonb_build_object('reminderStatus', payload::jsonb -> 'status')
)::text
where payload::jsonb ? 'status'
  and not payload::jsonb ? 'reminderStatus';

alter table reminder_scheduled_event_outbox
    alter column event_type set not null,
    alter column event_version set not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_reminder_scheduled_event_outbox_event_type'
    ) then
        alter table reminder_scheduled_event_outbox
            add constraint chk_reminder_scheduled_event_outbox_event_type check (length(trim(event_type)) > 0);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_reminder_scheduled_event_outbox_event_version'
    ) then
        alter table reminder_scheduled_event_outbox
            add constraint chk_reminder_scheduled_event_outbox_event_version check (length(trim(event_version)) > 0);
    end if;
end $$;
