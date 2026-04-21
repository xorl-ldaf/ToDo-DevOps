# Architecture Overview

## Why this shape

The project is intentionally structured as onion/hexagonal architecture instead of a single Spring Boot module with controllers talking directly to repositories. The goal is to keep domain and application logic stable while infrastructure concerns remain replaceable at the edges.

That matters for this repository because the same core application logic is exercised through:

- HTTP inbound adapters
- Kafka inbound integration
- JPA persistence
- Kafka outbound publishing
- Telegram outbound delivery
- different runtime environments such as local Compose and Kubernetes

## Module map

The actual Gradle module structure is:

- `core/domain`
  Pure business model: users, tasks, reminders, identifiers, state transitions, and domain exceptions.
- `core/application`
  Use cases, commands, ports, integration contracts, and application-level services.
- `adapters/in/web-rest`
  REST controllers, DTOs, validation, API mapping, and exception handling.
- `adapters/in/messaging-kafka`
  Kafka consumer for reminder-scheduled events and its observability hooks.
- `adapters/out/persistence-jpa`
  JPA entities, Spring Data repositories, mappers, and persistence adapters.
- `adapters/out/messaging-kafka`
  Kafka publisher for `ReminderScheduledEventV1`.
- `adapters/out/messaging-telegram`
  Telegram HTTP client adapter for reminder notification delivery.
- `apps/web-app`
  Spring Boot bootstrapping, bean wiring, runtime configuration, scheduler, and profile-specific config.

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
- `application` depends on ports such as `SaveTaskPort`, `PublishReminderScheduledEventPort`, and `DeliverReminderNotificationPort`.
- adapters implement those ports and translate to framework or infrastructure specifics.
- `apps/web-app` composes everything together through Spring configuration classes.

## Main runtime flows

### REST CRUD flow

1. `adapters/in/web-rest` receives HTTP requests under `/api/users`, `/api/tasks`, and `/api/tasks/{taskId}/reminders`.
2. DTOs are mapped into application commands.
3. Application services execute use cases.
4. Persistence adapters translate domain objects to JPA entities and store them in PostgreSQL.

### Reminder scheduling flow

1. A reminder is created through the REST API.
2. `CreateReminderService` saves it through the persistence port.
3. The application publishes `ReminderScheduledEventV1` through `PublishReminderScheduledEventPort`.
4. If Kafka is enabled, `adapters/out/messaging-kafka` publishes the event to the configured topic.
5. `adapters/in/messaging-kafka` consumes the event and records metrics/logs.

Important current boundary:

- the Kafka consumer does not trigger reminder delivery
- Kafka is the event/integration baseline, not the delivery execution path

### Reminder delivery flow

1. `apps/web-app` enables `ReminderDeliveryScheduler` when `todo.reminder-delivery.enabled=true`.
2. The scheduler calls `ScanDueRemindersUseCase`.
3. Persistence loads due reminders using `FOR UPDATE SKIP LOCKED`.
4. The application resolves the task and the assignee.
5. If the user has `telegramChatId`, the notification is sent through `DeliverReminderNotificationPort`.
6. The Telegram adapter performs HTTP delivery, timeout handling, retries, metrics, and failure classification.
7. Successful deliveries move reminders to `PUBLISHED`; permanent failures move them to `FAILED`.

## Why the DevOps pieces are part of the architecture

This project is not only a Java CRUD sample. The operational parts are part of the design:

- `compose.yaml` is the local all-in-one environment with Postgres, Kafka, Prometheus, and Grafana.
- `Dockerfile` produces the runtime image used by CI and smoke tests.
- `.github/workflows/ci.yaml` builds, tests, publishes, smoke-tests, scans, signs, and attests the image.
- `.github/workflows/deploy.yaml` promotes an immutable digest into Kubernetes instead of rebuilding during deploy.
- `deploy/k8s/` contains the tracked deployment baseline for the application workload.
- `observability/` contains Prometheus and Grafana provisioning that makes the metrics usable locally.
- `scripts/` contains operator helpers for Kustomize rendering and published-image verification.

## Why Kafka, Kubernetes, Grafana, and Telegram exist here

- Kafka exists to demonstrate an explicit event-driven integration boundary, not to replace the core reminder-delivery scheduler.
- Telegram exists to demonstrate a real outbound adapter with external HTTP integration, retries, timeout discipline, and failure handling.
- Kubernetes exists to show application deployment and rollout mechanics without pretending the repository is already a full platform repo.
- Prometheus and Grafana exist to make operational signals visible during local development and portfolio review.

## Current boundaries

Several things are deliberately not present in this checkout:

- no in-cluster deployment of PostgreSQL, Kafka, Prometheus, or Grafana
- no outbox or saga/orchestration layer
- no distributed tracing
- no centralized logging stack
- no GitOps controller, Argo CD, or Helm chart
- no HPA, PDB, NetworkPolicy, or secret manager integration

Those omissions are intentional. The repository demonstrates a coherent baseline, not a fictional full production platform.
