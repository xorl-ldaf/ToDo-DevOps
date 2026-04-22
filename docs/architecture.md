# Architecture Overview

## Why this shape

The repository is intentionally built as onion/hexagonal architecture instead of a single Spring Boot module with controllers, repositories, Kafka code, and HTTP clients mixed together.

The reasons are practical, not stylistic:

- reminder state transitions live in `core/domain`;
- consistency and use-case contracts live in `core/application`;
- Spring/JPA/Kafka/Telegram details stay in adapters or `apps/web-app`;
- runtime wiring can change without dragging infrastructure concerns into domain code.

## Module map

The actual Gradle module structure is:

- `core/domain`
  Domain model: users, tasks, reminders, identifiers, state transitions, and domain exceptions.
- `core/application`
  Use cases, commands, ports, integration contracts, outbox/receipt models, and application services.
- `adapters/in/web-rest`
  REST controllers, DTOs, validation, API mapping, and exception handling.
- `adapters/in/messaging-kafka`
  Kafka consumer for `ReminderScheduledEventV1`.
- `adapters/out/persistence-jpa`
  JPA entities, Spring Data repositories, migrations mapping support, and persistence adapters.
- `adapters/out/messaging-kafka`
  Kafka publisher for reminder-scheduled integration events.
- `adapters/out/messaging-telegram`
  Telegram HTTP client adapter for reminder notification delivery.
- `apps/web-app`
  Spring Boot bootstrapping, bean wiring, schedulers, runtime configuration, and profile-specific config.

## Layer boundaries

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
Spring Boot app wiring / runtime config
```

Practical interpretation in this repository:

- `domain` does not know about Spring, HTTP, Kafka, JPA, or Telegram.
- `application` depends on ports such as `SaveReminderPort`, `StoreReminderScheduledEventPort`, `ClaimDueRemindersPort`, `FinalizeReminderDeliveryPort`, and `PublishReminderScheduledEventPort`.
- adapters implement those ports and translate to framework/infrastructure specifics.
- `apps/web-app` composes everything together through Spring configuration.

## Reminder model

### Reminder states

The reminder lifecycle is now explicit and semantically aligned with actual runtime behavior:

- `SCHEDULED`
  Reminder is waiting for `nextAttemptAt`.
- `PROCESSING`
  Reminder is claimed by a worker lease and external delivery is in progress.
- `DELIVERED`
  Telegram accepted the reminder notification.
- `FAILED`
  Reminder reached a terminal failure condition.

There is no longer a misleading split between `PUBLISHED` and `SENT`.

### Reminder fields that matter operationally

The reminder aggregate now carries the state required for reliable background processing:

- `nextAttemptAt`
- `processingStartedAt`
- `processingOwner`
- `deliveryAttempts`
- `deliveredAt`
- `lastFailureReason`

These fields exist because the delivery path is a real background workflow, not a fire-and-forget loop.

## Main runtime flows

### REST CRUD flow

1. `adapters/in/web-rest` receives HTTP requests under `/api/users`, `/api/tasks`, and `/api/tasks/{taskId}/reminders`.
2. DTOs are mapped into application commands.
3. Application services execute use cases.
4. Persistence adapters translate domain objects to JPA entities and store them in PostgreSQL.

### Reminder creation flow

1. `CreateReminderService` validates the task and creates a `SCHEDULED` reminder.
2. The reminder is saved through the reminder persistence port.
3. If Kafka integration is enabled, the matching `ReminderScheduledEventV1` is stored in the outbox through `StoreReminderScheduledEventPort`.
4. The REST request returns success only after both writes commit in one transaction.

Resulting contract:

- reminder row is durable;
- if Kafka integration is enabled, the event is also durable in the outbox;
- synchronous Kafka publication is not part of the request contract.

### Kafka outbox flow

1. `ReminderScheduledEventOutboxScheduler` claims pending outbox rows in short transactions.
2. The worker publishes to Kafka outside the transaction.
3. The outbox row is finalized as `PUBLISHED`, rescheduled for retry, or marked `FAILED`.

This is a baseline outbox pattern, not an exactly-once cross-system guarantee.

### Kafka consumer / receipt flow

1. `adapters/in/messaging-kafka` consumes `ReminderScheduledEventV1`.
2. The consumer records an idempotent receipt row keyed by `eventId`.
3. Metrics/logs expose consume throughput, lag, failures, retries, and duplicate receipts.

This gives Kafka a real operational role:

- durable outbound integration boundary
- downstream consumer behavior
- receipt/audit trail for reconciliation

### Reminder delivery flow

1. `ReminderDeliveryScheduler` invokes `ScanDueRemindersUseCase`.
2. Persistence claims due reminders using `FOR UPDATE SKIP LOCKED` and short claim transactions.
3. The worker resolves task/assignee data.
4. Telegram delivery happens outside the database transaction.
5. Finalization happens in a new short transaction:
   - `DELIVERED` on success
   - `SCHEDULED` with a later `nextAttemptAt` on retryable failure
   - `FAILED` on terminal failure

Important properties of the new design:

- no external HTTP inside the claim transaction
- no `Thread.sleep` in the transaction path
- lease-based claim/reclaim behavior for stuck workers
- explicit retry budget and backoff policy

## Kafka role in this project

Kafka is intentionally not the same thing as reminder execution.

Kafka exists here to model a durable integration boundary for “reminder was scheduled”, while Telegram delivery remains a separate background concern. That is why:

- creating a reminder goes through the outbox;
- Kafka consumer receipts are stored for audit/reconciliation;
- the reminder delivery worker does not depend on Kafka consumption.

## Why the DevOps pieces are part of the architecture

This project is not only a Java CRUD sample. The operational assets are part of the system shape:

- `compose.yaml` gives a local environment with Postgres, Kafka, Prometheus, and Grafana.
- `Dockerfile` produces the hardened runtime image.
- `.github/workflows/ci.yaml` builds, tests, scans, signs, and attests the image.
- `.github/workflows/deploy.yaml` promotes an immutable digest into Kubernetes.
- `deploy/k8s/` contains the workload deployment baseline.
- `observability/` contains Prometheus and Grafana provisioning.

## Current boundaries

Several things are deliberately still out of scope:

- no in-cluster deployment of PostgreSQL, Kafka, Prometheus, or Grafana
- no distributed tracing
- no centralized logging stack
- no NetworkPolicy or secret-manager integration
- no end-to-end exactly-once delivery across PostgreSQL, Kafka, and Telegram

The important part is that those limits are explicit in the code and docs instead of being masked by misleading semantics.
