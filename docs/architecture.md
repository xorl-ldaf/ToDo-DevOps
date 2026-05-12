# Architecture

This repository uses hexagonal architecture for a practical reason: the interesting parts of the project are the use-case contracts and reliability trade-offs, not Spring annotations. Domain and application code stay isolated from REST, JPA, Kafka, Telegram, and deployment wiring so those adapters can change without rewriting business logic.

## Module Map

- `core/domain`
  Users, tasks, reminders, value objects, lifecycle states, and domain validation.
- `core/application`
  Use cases, commands, ports, services, reminder event contracts, outbox message model, and Kafka receipt model.
- `adapters/in/web-rest`
  REST controllers, DTOs, validation, API mapping, and exception handling.
- `adapters/in/messaging-kafka`
  Kafka consumer for `ReminderScheduledEventV1`.
- `adapters/out/persistence-jpa`
  JPA entities, Spring Data repositories, and persistence adapters for PostgreSQL.
- `adapters/out/messaging-kafka`
  Kafka publisher implementation for reminder-scheduled events.
- `adapters/out/messaging-telegram`
  Telegram HTTP adapter for reminder notification delivery.
- `apps/web-app`
  Spring Boot entrypoint, bean wiring, schedulers, runtime properties, profiles, and Flyway migrations.

## Dependency Direction

```text
domain
  ^
  |
application services + ports
  ^                    |
  |                    v
inbound adapters   outbound adapters
  ^
  |
Spring Boot runtime wiring
```

The main rules:

- `core/domain` does not depend on Spring, JPA, Kafka, HTTP, or Telegram.
- `core/application` depends on ports such as `SaveReminderPort`, `ClaimDueRemindersPort`, `FinalizeReminderDeliveryPort`, `StoreReminderScheduledEventPort`, and `PublishReminderScheduledEventPort`.
- inbound adapters call application use cases.
- outbound adapters implement application ports.
- `apps/web-app` wires concrete adapters into the application.

This keeps the codebase testable at multiple levels: domain tests, application service tests with fake ports, adapter tests, and Spring integration tests.

## REST API Surface

The implemented HTTP API is intentionally small:

- `POST /api/users`
- `GET /api/users`
- `GET /api/users/{userId}`
- `POST /api/tasks`
- `GET /api/tasks`
- `GET /api/tasks/{taskId}`
- `PATCH /api/tasks/{taskId}/assign`
- `POST /api/tasks/{taskId}/reminders`
- `GET /api/tasks/{taskId}/reminders`

There is no authentication/authorization layer in the current API.

## Reminder Model

Reminder lifecycle states:

- `SCHEDULED` - reminder is waiting for `nextAttemptAt`.
- `PROCESSING` - a worker has claimed the reminder.
- `DELIVERED` - Telegram accepted the notification.
- `FAILED` - processing reached a terminal failure.

Operational fields stored in PostgreSQL:

- `nextAttemptAt`
- `processingStartedAt`
- `processingOwner`
- `deliveryAttempts`
- `deliveredAt`
- `lastFailureReason`

These fields make the worker recoverable after crashes and allow concurrent app instances to share work through database locking.

## Reminder Creation Boundary

`CreateReminderService` validates that the task exists and creates a `SCHEDULED` reminder. In `apps/web-app`, this use case is wrapped by `TransactionalCreateReminderUseCase`.

When Kafka is disabled:

1. the reminder row is saved;
2. the outbox port is a no-op;
3. the transaction commits.

When Kafka is enabled:

1. the reminder row is saved;
2. a `ReminderScheduledEventV1` row is stored in `reminder_scheduled_event_outbox`;
3. both writes commit in the same PostgreSQL transaction.

Kafka publication is not part of the REST transaction. The API guarantees durable intent to publish, not synchronous publication.

## Kafka Integration Boundary

Kafka is used as an integration boundary for "a reminder was scheduled". It is deliberately not the reminder delivery execution path.

Outbound flow:

1. reminder creation stores an outbox row when Kafka is enabled;
2. `ReminderScheduledEventOutboxScheduler` claims pending outbox rows;
3. `KafkaReminderScheduledEventPublisher` publishes to Kafka;
4. the outbox row is marked `PUBLISHED`, rescheduled, or marked `FAILED`.

Inbound flow:

1. `KafkaReminderScheduledEventConsumer` consumes `ReminderScheduledEventV1`;
2. the consumer writes a receipt row keyed by `eventId`;
3. duplicate deliveries are counted and ignored by the receipt persistence path.

This gives the project a real Kafka path for integration, audit, lag metrics, and duplicate handling. It does not create exactly-once behavior across PostgreSQL and Kafka.

## Reminder Worker Transaction Boundaries

The reminder delivery worker avoids long transactions around external HTTP:

1. Claim due reminders in a short transaction using `FOR UPDATE SKIP LOCKED`.
2. Load task and assignee data.
3. Call Telegram outside the claim transaction.
4. Finalize in a new short transaction by checking the claimed reminder and `processingOwner`.

Finalization outcomes:

- `DELIVERED` when Telegram accepts the message.
- `SCHEDULED` with a later `nextAttemptAt` when the failure is retryable and attempts remain.
- `FAILED` when the task/assignee is missing, the user has no Telegram chat ID, Telegram rejects permanently, or retry budget is exhausted.

If a process dies while a reminder is `PROCESSING`, a later scan can reclaim it after `processingTimeout`.

## Consistency and Failure Semantics

- Reminder creation is durable in PostgreSQL after the request succeeds.
- With Kafka enabled, the outbox row is committed in the same transaction as the reminder row.
- Kafka publication is asynchronous and at-least-once.
- Duplicate Kafka publication or consumption can happen.
- Kafka receipt persistence is idempotent by `eventId` and also tracks topic/partition/offset.
- Telegram delivery is at-least-once because Telegram `sendMessage` does not give this code path an idempotency key.
- A crash after Telegram accepts a message but before `markDelivered` commits can lead to a duplicate Telegram message.

## DevOps Shape

The operational files are part of the architecture rather than disconnected examples:

- `compose.yaml` runs the local app stack with PostgreSQL, Kafka, Prometheus, and Grafana.
- `Dockerfile` builds the Spring Boot app and runs it as a non-root user.
- `.github/workflows/ci.yaml` validates Compose, builds/tests, builds and smoke-tests the image, scans it, signs it, and creates/verifies attestations.
- `.github/workflows/deploy.yaml` deploys a previously published immutable image digest.
- `deploy/k8s/` contains Kustomize base and local/prod overlays for the app workload.
- `observability/` contains Prometheus and Grafana provisioning.

## Out of Scope

- full production security model
- authentication and authorization
- distributed tracing
- centralized log aggregation
- in-cluster PostgreSQL/Kafka/Prometheus/Grafana provisioning
- secret-manager integration
- exactly-once delivery across PostgreSQL, Kafka, and Telegram
