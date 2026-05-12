# ADR-002: Transactional Outbox for Reminder-Scheduled Events

## Status

Accepted.

## Context

Creating a reminder and publishing a Kafka event cannot be made atomic with a normal PostgreSQL transaction. Publishing directly to Kafka inside the request path would risk saved reminders without durable integration events, or published events for data that later rolls back.

## Decision

When Kafka is enabled, reminder creation stores `ReminderScheduledEventV1` in `reminder_scheduled_event_outbox` in the same transaction as the reminder row. A scheduled outbox worker publishes events asynchronously and finalizes the outbox row.

## Consequences

- A successful request durably stores both the reminder and the intent to publish.
- Kafka publication is asynchronous and at-least-once.
- Duplicate Kafka publication remains possible after failures or restarts.
- Consumers must be idempotent; this project persists receipts by `eventId`.
