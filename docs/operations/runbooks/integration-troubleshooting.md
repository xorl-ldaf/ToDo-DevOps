# Runbook: Kafka and Telegram Integration Troubleshooting

## Goal

Debug the optional integration baselines without confusing the reminder-delivery worker with the Kafka boundary.

## Important distinction

- Kafka integration emits durable reminder-scheduled events from the outbox.
- Kafka consumption persists receipt/audit rows and metrics.
- Telegram integration delivers due reminders through the reminder delivery worker.
- Telegram delivery does not depend on Kafka consumption.

## Kafka reminder-scheduling checks

### Preconditions

- `TODO_KAFKA_ENABLED=true`
- `TODO_KAFKA_BOOTSTRAP_SERVERS` points to a reachable broker

### Expected behavior

1. Creating a reminder stores it in PostgreSQL.
2. The same transaction stores `ReminderScheduledEventV1` in `reminder_scheduled_event_outbox`.
3. The outbox worker publishes to Kafka asynchronously.
4. The Kafka consumer receives the event.
5. A receipt row is stored in `reminder_scheduled_event_receipts`.

### Verification

```bash
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_kafka_outbox|todo_reminder_scheduled'
docker compose logs app --tail=200 | rg 'outbox|Kafka|reminder scheduled'
```

Helpful SQL:

```sql
select event_id, status, delivery_attempts, last_failure_reason
from reminder_scheduled_event_outbox
order by created_at desc
limit 20;

select event_id, consumed_at, topic, kafka_partition, kafka_offset
from reminder_scheduled_event_receipts
order by consumed_at desc
limit 20;
```

### Failure signals

- `todo.kafka.outbox.scans{outcome="failure"}`
- `todo.kafka.outbox.results{outcome="retried"|"failed"}`
- `todo.reminder.scheduled.events.publish.failures`
- `todo.reminder.scheduled.events.failed`
- `todo.reminder.scheduled.events.receipts{outcome="duplicate"}`

## Telegram reminder-delivery checks

### Preconditions

- `TODO_TELEGRAM_ENABLED=true`
- `TODO_TELEGRAM_BOT_TOKEN` is set
- `TODO_REMINDER_DELIVERY_ENABLED=true`
- the target user has a non-null `telegramChatId`
- the reminder is due

### Expected behavior

1. The worker claims due reminders with a short transaction.
2. The worker performs Telegram HTTP outside the database transaction.
3. Success finalizes the reminder as `DELIVERED`.
4. Retryable failures move the reminder back to `SCHEDULED` with a later `next_attempt_at`.
5. Terminal failures move the reminder to `FAILED`.

### Verification

```bash
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_reminder_delivery'
docker compose logs app --tail=200 | rg 'Telegram|Reminder delivery'
```

Helpful SQL:

```sql
select id, status, next_attempt_at, processing_started_at, processing_owner, delivery_attempts, last_failure_reason
from reminders
order by updated_at desc
limit 20;
```

### Failure signals

- `todo.reminder.delivery.scans{outcome="failure"}`
- `todo.reminder.delivery.results{outcome="retried"|"failed"|"conflict"}`
- `todo.reminder.delivery.attempts{outcome="retryable_failure"|"permanent_failure"}`

## When neither integration behaves as expected

Check these in order:

1. application logs
2. active environment variables
3. `/actuator/prometheus`
4. database reachability
5. Kafka or Telegram endpoint reachability

Do not assume the integrations break in the same place:

- Kafka issues usually show up in the outbox worker after reminder creation
- Telegram issues usually show up when reminders become due
