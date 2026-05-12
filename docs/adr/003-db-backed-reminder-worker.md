# ADR-003: DB-Backed Reminder Worker

## Status

Accepted.

## Context

Reminder delivery must survive process restarts and should work with more than one application instance. The project also needs to keep Telegram delivery independent from Kafka consumption.

## Decision

Use PostgreSQL as the execution source for reminders. The worker claims due reminders with `FOR UPDATE SKIP LOCKED`, marks them `PROCESSING`, performs external delivery outside the claim transaction, and finalizes with a short transaction.

## Consequences

- Reminder execution is durable and recoverable.
- Competing workers can share work without a separate queue.
- Stale `PROCESSING` reminders can be reclaimed after `processingTimeout`.
- PostgreSQL is on the critical path for scheduling and delivery.
- Kafka receipts remain audit/reconciliation data, not the execution path.
