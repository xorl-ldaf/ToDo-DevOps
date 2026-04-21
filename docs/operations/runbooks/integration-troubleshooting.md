# Runbook: Kafka and Telegram Integration Troubleshooting

## Goal

Debug the two optional integration baselines that exist in this repository without confusing them with each other.

## Important distinction

- Kafka integration publishes and consumes reminder-scheduled events.
- Telegram integration delivers due reminders through the scheduler.
- Telegram delivery does not depend on the Kafka consumer path in the current design.

## Kafka reminder-scheduling checks

### Kafka preconditions

- `TODO_KAFKA_ENABLED=true`
- `TODO_KAFKA_BOOTSTRAP_SERVERS` points to a reachable broker

### Kafka expected behavior

1. Creating a reminder stores it in PostgreSQL.
2. The app publishes `ReminderScheduledEventV1`.
3. The Kafka consumer receives the event.
4. Metrics and logs reflect the publish/consume flow.

### Kafka verification

```bash
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_reminder_scheduled'
docker compose logs app --tail=200 | rg 'Kafka|reminder scheduled'
```

If you are using Docker Compose, confirm Kafka is healthy:

```bash
docker compose ps kafka
docker compose logs kafka --tail=200
```

### Common Kafka problems

- `TODO_KAFKA_ENABLED=true` but the broker is not reachable
- wrong `TODO_KAFKA_BOOTSTRAP_SERVERS`
- wrong topic name in `TODO_KAFKA_TOPIC_REMINDER_SCHEDULED_V1`
- wrong consumer group configuration when comparing environments

### Kafka failure signals

- publish failures increment `todo.reminder.scheduled.events.publish.failures`
- consumer failures increment `todo.reminder.scheduled.events.failed`
- retry attempts increment `todo.reminder.scheduled.events.retries`

## Telegram reminder-delivery checks

### Telegram preconditions

- `TODO_TELEGRAM_ENABLED=true`
- `TODO_TELEGRAM_BOT_TOKEN` is set
- `TODO_REMINDER_DELIVERY_ENABLED=true`
- the target user has a non-null `telegramChatId`
- the reminder is due

### Telegram expected behavior

1. The scheduler scans due reminders.
2. Due reminders are locked and processed in a transaction.
3. The Telegram adapter calls `sendMessage`.
4. Successful deliveries move reminders to `PUBLISHED`.
5. Permanent Telegram failures move reminders to `FAILED`.

### Telegram verification

```bash
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_reminder_delivery'
docker compose logs app --tail=200 | rg 'Telegram|Reminder delivery'
```

If you want to test against a stub instead of the real Telegram API, point:

- `TODO_TELEGRAM_BASE_URL` at your stub server

### Common Telegram problems

- missing `TODO_TELEGRAM_BOT_TOKEN` when Telegram is enabled
- user exists but has no `telegramChatId`
- `TODO_REMINDER_DELIVERY_ENABLED=false`
- permanent Telegram HTTP `4xx` responses
- retryable `429` or `5xx` responses that keep reminders pending for later scans

### Telegram failure signals

- retries increment `todo.reminder.delivery.retries`
- scan failures increment `todo.reminder.delivery.scans` with failure outcome
- delivery attempt metrics show `retryable_failure` or `permanent_failure`

## When neither integration behaves as expected

Check these in order:

1. application logs
2. active environment variables
3. `/actuator/prometheus`
4. database reachability
5. Kafka or Telegram endpoint reachability

Do not assume the integrations are broken in the same way. Kafka issues usually appear on reminder creation. Telegram issues usually appear when the reminder becomes due and the scheduler runs.
