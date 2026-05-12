# ToDo-DevOps

ToDo-DevOps is a production-oriented portfolio project: a Java 21 / Spring Boot backend for users, tasks, and reminders, built to show backend engineering and DevOps judgment without pretending to be a complete production platform.

The project is intentionally more than CRUD. It demonstrates PostgreSQL/Flyway persistence, hexagonal architecture, background reminder processing, an outbox-backed Kafka integration boundary, idempotent Kafka receipt persistence, Telegram delivery, Docker Compose, Kubernetes manifests, observability, and CI supply-chain awareness.

## What This Project Demonstrates

- REST API design for users, tasks, task assignment, and task reminders.
- PostgreSQL schema management through Flyway migrations.
- Hexagonal architecture with domain/application logic separated from Spring, JPA, Kafka, Telegram, and web adapters.
- DB-backed reminder worker with `SCHEDULED`, `PROCESSING`, `DELIVERED`, and `FAILED` states.
- Short transaction boundaries for claiming and finalizing background work.
- Transactional outbox storage for `ReminderScheduledEventV1` when Kafka integration is enabled.
- Asynchronous Kafka publication with retries and failure tracking.
- Idempotent Kafka receipt persistence for audit/reconciliation.
- Telegram adapter with explicit retryable/non-retryable failure classification.
- Local operational stack with Docker Compose, Prometheus, and Grafana.
- Kubernetes deployment baseline with Kustomize overlays.
- CI pipeline awareness: Gradle verification, Compose validation, image build/smoke test, Trivy scan, SBOM, signing, and attestations.

## Architecture Overview

```text
HTTP clients / tests
        |
        v
adapters/in/web-rest          adapters/in/messaging-kafka
        |                                |
        v                                v
core/application  <---------------  receipt use case
        |
        v
core/domain
        |
        +----------------+----------------+----------------+
        v                v                v                v
 persistence-jpa   messaging-kafka   messaging-telegram   web-app wiring
 PostgreSQL        Kafka publisher    Telegram HTTP       Spring config
```

The core business rules do not depend on Spring or infrastructure APIs. Application services define use cases and ports; adapters implement those ports for REST, JPA, Kafka, and Telegram. `apps/web-app` composes the runtime.

More detail: [docs/architecture.md](docs/architecture.md).

## Module Layout

- `core/domain` - user, task, reminder aggregates, IDs, enums, and domain validation.
- `core/application` - use cases, commands, ports, services, outbox messages, Kafka event contracts, and receipt model.
- `adapters/in/web-rest` - REST controllers, request/response DTOs, validation, and exception mapping.
- `adapters/in/messaging-kafka` - Kafka consumer for reminder-scheduled events.
- `adapters/out/persistence-jpa` - JPA entities, repositories, and persistence adapters.
- `adapters/out/messaging-kafka` - Kafka publisher adapter.
- `adapters/out/messaging-telegram` - Telegram `sendMessage` adapter.
- `apps/web-app` - Spring Boot entrypoint, configuration, schedulers, profiles, and Flyway migrations.

## Reminder Lifecycle

Reminder execution is database-backed and independent from Kafka consumption:

1. `POST /api/tasks/{taskId}/reminders` validates the task and stores a `SCHEDULED` reminder.
2. If Kafka is enabled, the same transaction stores a `ReminderScheduledEventV1` row in `reminder_scheduled_event_outbox`.
3. The Kafka outbox scheduler later publishes pending outbox rows and marks them `PUBLISHED`, retries them, or marks them `FAILED`.
4. The reminder delivery scheduler claims due reminders using `FOR UPDATE SKIP LOCKED`, moving them to `PROCESSING`.
5. Telegram delivery happens outside the claim transaction.
6. A final short transaction marks the reminder `DELIVERED`, reschedules it as `SCHEDULED`, or marks it `FAILED`.

## Consistency Guarantees

- A successfully created reminder is durable in PostgreSQL.
- When `TODO_KAFKA_ENABLED=true`, the outbox event is durable in the same database transaction as the reminder.
- Kafka publication is asynchronous and at-least-once; the REST request does not wait for Kafka publication.
- Duplicate Kafka publication is possible after failures or restarts.
- The Kafka consumer stores receipts idempotently by `eventId`, so duplicate deliveries are recorded as duplicates instead of creating duplicate receipt rows.
- Kafka receipt persistence is for audit/reconciliation and metrics. It is not the reminder delivery execution path.

## Known Limitations

- This is a production-oriented educational backend, not a complete production system.
- There is no authentication or authorization layer in the current API.
- There is no distributed tracing or centralized log aggregation in the repository.
- Kubernetes manifests deploy the application workload only; they do not provision PostgreSQL, Kafka, Prometheus, or Grafana in-cluster.
- Kafka does not provide exactly-once behavior across PostgreSQL, Kafka, and the consumer.
- Telegram delivery is at-least-once. If the process crashes after Telegram accepts a message but before the reminder is finalized as `DELIVERED`, the reminder can be retried and the user can receive a duplicate message.
- Secrets in local examples and Kubernetes overlay files are placeholders, not a secret-management solution.

## Quick Start

Create local configuration:

```bash
cp .env.example .env
```

Start the full local stack:

```bash
docker compose --env-file .env up --build
```

Verify the app:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/api/users
```

Local URLs:

- App API: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

The Compose stack starts PostgreSQL, Kafka, the app, Prometheus, and Grafana. It enables Kafka integration for the app container by default and leaves Telegram disabled unless you opt in with `TODO_TELEGRAM_ENABLED=true`, `TODO_TELEGRAM_BOT_TOKEN`, and reminder-delivery settings.

## API Examples

The examples assume the app is running on `localhost:8080` and use `jq` only to capture IDs.

Create a user:

```bash
USER_ID="$(
  curl --fail --silent -X POST http://localhost:8080/api/users \
    -H 'Content-Type: application/json' \
    -d '{
      "username": "alice",
      "displayName": "Alice Example",
      "telegramChatId": 123456789
    }' | jq -r '.id'
)"
```

Create a task:

```bash
TASK_ID="$(
  curl --fail --silent -X POST http://localhost:8080/api/tasks \
    -H 'Content-Type: application/json' \
    -d "{
      \"title\": \"Prepare backend interview notes\",
      \"description\": \"Summarize architecture and reliability trade-offs\",
      \"authorId\": \"${USER_ID}\",
      \"priority\": \"HIGH\",
      \"dueAt\": \"2026-06-01T12:00:00Z\"
    }" | jq -r '.id'
)"
```

Create another user and assign the task:

```bash
ASSIGNEE_ID="$(
  curl --fail --silent -X POST http://localhost:8080/api/users \
    -H 'Content-Type: application/json' \
    -d '{
      "username": "bob",
      "displayName": "Bob Reviewer"
    }' | jq -r '.id'
)"

curl --fail --silent -X PATCH "http://localhost:8080/api/tasks/${TASK_ID}/assign" \
  -H 'Content-Type: application/json' \
  -d "{
    \"assigneeId\": \"${ASSIGNEE_ID}\"
  }"
```

Create a reminder:

```bash
curl --fail --silent -X POST "http://localhost:8080/api/tasks/${TASK_ID}/reminders" \
  -H 'Content-Type: application/json' \
  -d '{
    "remindAt": "2026-06-01T09:00:00Z"
  }'
```

List reminders for a task:

```bash
curl --fail --silent "http://localhost:8080/api/tasks/${TASK_ID}/reminders"
```

## Tests and Verification

Run tests:

```bash
./gradlew test --no-daemon
```

Build the application:

```bash
./gradlew clean build --no-daemon
```

Validate Compose files:

```bash
docker compose -f compose.yaml config -q
docker compose -f compose.smoke.yaml config -q
```

Render Kubernetes overlays:

```bash
kubectl kustomize deploy/k8s/overlays/local
kubectl kustomize deploy/k8s/overlays/prod
```

These commands match the local files and CI workflow. Commands that require Docker, Kubernetes, or Testcontainers need those tools available locally.

## Observability and DevOps

- Spring Boot actuator health/readiness/liveness and Prometheus metrics.
- Custom metrics for reminder delivery, Telegram attempts, Kafka outbox scans/results, Kafka publish/consume behavior, and receipt persistence.
- Local Prometheus and Grafana provisioning under `observability/`.
- Docker image with non-root runtime user and `/tmp` as the writable path.
- Kubernetes manifests with probes, resource settings, security context, service, ingress, PDB, and local/prod overlays.
- CI pipeline validates Compose files, runs Gradle build/tests, builds and smoke-tests the image, scans with Trivy, generates an SBOM, signs the image, and creates/verifies attestations.

## Documentation Map

- [Architecture](docs/architecture.md)
- [Deployment](docs/deployment.md)
- [Observability](docs/observability.md)
- [Security and supply chain](docs/security-supply-chain.md)
- [ADR-001 Hexagonal architecture](docs/adr/001-hexagonal-architecture.md)
- [ADR-002 Transactional outbox](docs/adr/002-transactional-outbox.md)
- [ADR-003 DB-backed reminder worker](docs/adr/003-db-backed-reminder-worker.md)
- [ADR-004 Telegram delivery limitations](docs/adr/004-telegram-delivery-limitations.md)
- [Local startup runbook](docs/operations/runbooks/local-startup.md)
- [Health verification runbook](docs/operations/runbooks/health-verification.md)
- [Failed startup runbook](docs/operations/runbooks/failed-startup.md)
- [Post-deploy smoke runbook](docs/operations/runbooks/post-deploy-smoke.md)
- [Rollback first checks runbook](docs/operations/runbooks/rollback-first-checks.md)
- [Integration troubleshooting runbook](docs/operations/runbooks/integration-troubleshooting.md)
