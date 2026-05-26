# ToDo-DevOps

[![CI](https://github.com/xorl-ldaf/ToDo-DevOps/actions/workflows/ci.yaml/badge.svg)](https://github.com/xorl-ldaf/ToDo-DevOps/actions/workflows/ci.yaml)

ToDo-DevOps is a production-inspired portfolio project: a Java 21 / Spring Boot backend for users, tasks, and reminders. It is intentionally larger than a minimal ToDo app so the codebase can show architecture, reliability, testing, and DevOps trade-offs without pretending to be a complete production platform.

The useful interview signal is not "this app needs Kubernetes and Kafka". It is that each extra piece has a visible boundary, a failure mode, and a place where I can explain why I would keep it, simplify it, or remove it for a different product size.

## What This Project Demonstrates

- REST API design for users, tasks, task assignment, and task reminders.
- PostgreSQL schema management through Flyway migrations.
- Hexagonal architecture with domain/application logic separated from Spring, JPA, Kafka, Telegram, and web adapters.
- ArchUnit tests that fail the build if onion boundaries are broken.
- DB-backed reminder worker with `SCHEDULED`, `PROCESSING`, `DELIVERED`, and `FAILED` states.
- Short transaction boundaries for claiming and finalizing background work.
- Transactional outbox storage for `ReminderScheduledEventV1` when Kafka integration is enabled.
- Asynchronous Kafka publication with retries and failure tracking.
- Idempotent Kafka receipt persistence for audit/reconciliation.
- Telegram adapter with explicit retryable/non-retryable failure classification.
- Local operational stack with Docker Compose, Prometheus, and Grafana.
- Kubernetes deployment baseline with Kustomize overlays.
- CI pipeline awareness: Gradle verification, Compose validation, image build/smoke test, Trivy scan, SBOM, signing, and attestations.

## Why This Is Intentionally More Than CRUD

A small ToDo API can be built as one Spring Boot module with controllers, services, and repositories. This repository goes further on purpose because CRUD alone does not show many production conversations:

- Hexagonal modules make it easy to discuss dependency direction and testability without Spring context.
- Flyway migrations and JPA adapters show how persistence behavior is validated against PostgreSQL rather than only mocked.
- The reminder worker creates real state-transition and transaction-boundary questions.
- The transactional outbox shows how to avoid publishing Kafka events directly inside the request transaction.
- Kafka receipt persistence demonstrates at-least-once delivery and idempotency handling.
- Telegram delivery creates a concrete external-call reliability problem with retryable and non-retryable failures.
- Docker Compose, K8s manifests, observability, and CI show how the app is packaged and checked, while keeping platform claims limited.

The point is to make trade-offs inspectable. The project is not claiming that every small ToDo product should start with this shape.

## What I Would Simplify In A Real Small ToDo

For a real small internal ToDo, I would cut aggressively:

- Use one Spring Boot module until module boundaries start paying for themselves.
- Remove Kafka and the outbox unless another service genuinely consumes task/reminder events.
- Keep reminders as a database-backed scheduled worker if durable reminders matter; otherwise start with a simpler in-process scheduler.
- Disable Telegram integration unless notification delivery is part of the product requirement.
- Use Docker Compose for local/dev and delay Kubernetes until there is a real deployment target.
- Keep CI to build/test/image/scan first; add signing, attestations, and SBOM verification when image promotion matters.
- Replace Kustomize `secretGenerator` placeholders with a real secret manager only when deploying to a shared or production cluster.

That simplification would reduce code and operational surface area. This portfolio version keeps the extra parts because they create useful engineering discussion.

## Known Limitations And Interview Answers

- No authentication/authorization.
  For production, I would add auth before exposing user/task data. It is omitted here to keep focus on backend boundaries, reliability, and DevOps.
- Not a full production platform.
  The repo has useful baselines, but no managed database, no in-cluster Kafka, no centralized logs, no tracing, no autoscaling policy, and no secret-manager integration.
- Kafka is not the reminder execution path.
  PostgreSQL drives reminder delivery. Kafka is used as an integration/audit boundary for scheduled reminder events.
- No exactly-once delivery claim.
  PostgreSQL plus Kafka plus Telegram cannot provide end-to-end exactly-once behavior here. The design uses durable state, idempotent receipts, and retry policies instead.
- Telegram delivery is at-least-once.
  Telegram `sendMessage` does not give this path an idempotency key. A crash after Telegram accepts a message but before DB finalization can produce a duplicate user-visible message.
- K8s manifests are an app workload baseline.
  They define Deployment, Service, Ingress, PDB, security context, probes, resources, and overlays. They do not provision PostgreSQL, Kafka, Prometheus, Grafana, or real production secrets.
- Supply-chain controls are demonstrative.
  CI includes Trivy, SBOM, signing, and attestations for the image path. That is useful evidence, not a blanket security certification.

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
- Secrets in `.env.example` and Kubernetes overlay files are placeholders. Real production should use GitHub Actions secrets for CI/deploy inputs and an external secret manager for Kubernetes workloads.

## Quick Start

Create local configuration:

```bash
cp .env.example .env
```

Replace the `change-me-*` values in `.env` before using the stack outside a throwaway local environment. Keep real database passwords, Telegram bot tokens, and Grafana passwords out of Git.

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

Core verification commands:

```bash
./gradlew clean build
docker compose config
docker compose -f compose.yaml config -q
docker build -t todo-devops:local .
kubectl kustomize deploy/k8s/overlays/local
```

Run tests:

```bash
./gradlew test --no-daemon
```

`test` is the unit/lightweight test task used by the normal build. It does not require production secrets or Docker.

Run Docker-backed integration tests:

```bash
./gradlew integrationTest --no-daemon --stacktrace
```

The integration suite uses Testcontainers for PostgreSQL/Kafka/Flyway/JPA behavior. It is separated from the ordinary unit build so `./gradlew clean build` stays deterministic without Docker or runtime secrets. If Docker is unavailable, Testcontainers-backed tests are skipped by their JUnit extension.

Build the application:

```bash
./gradlew clean build
./gradlew clean build --no-daemon
```

Build the container image:

```bash
docker build -t todo-devops:local .
```

Image assumptions:

- The image is a simple multi-stage build: Gradle builds the Spring Boot jar, then a JRE runtime image runs it.
- Base images use explicit version tags instead of digest pins so local rebuilds can pick up upstream security refreshes. Published deployment artifacts should still be promoted by immutable app-image digest.
- The runtime process runs as `10001:10001`.
- `/app` is read-only to the application user; `JAVA_TOOL_OPTIONS` and `HOME` point runtime scratch behavior at `/tmp`.
- `curl` is installed only for the image `HEALTHCHECK` against the actuator readiness endpoint.
- The app container expects PostgreSQL configuration through environment variables such as `TODO_DB_HOST`, `TODO_DB_PORT`, `TODO_DB_NAME`, `TODO_DB_USERNAME`, and `TODO_DB_PASSWORD`. Use Compose for the full local stack.

Validate Compose files:

```bash
docker compose -f compose.yaml config -q
docker compose -f compose.smoke.yaml config -q
```

Run the local equivalent of the core CI checks:

```bash
./gradlew clean build --no-daemon
./gradlew integrationTest --no-daemon --stacktrace
docker compose -f compose.yaml config -q
docker compose -f compose.smoke.yaml config -q
docker build -t todo-devops:local .
APP_IMAGE=todo-devops:local docker compose -f compose.smoke.yaml up -d --wait --wait-timeout 120
curl --fail --retry 20 --retry-delay 3 http://localhost:18080/actuator/health/readiness
curl --fail http://localhost:18080/api/users
docker compose -f compose.smoke.yaml down -v
```

Render Kubernetes overlays:

```bash
kubectl kustomize deploy/k8s/overlays/local
kubectl kustomize deploy/k8s/overlays/prod
```

These commands match the local files and CI workflow. Commands that require Docker, Kubernetes, or Testcontainers need those tools available locally.

Runtime-only configuration:

- `TODO_DB_*`, `TODO_KAFKA_*`, `TODO_TELEGRAM_*`, `TODO_GRAFANA_*`, and actuator exposure overrides are read by local Compose, smoke tests, Kubernetes overlays, or the running app.
- Normal `./gradlew clean build` and `./gradlew test` do not require real database passwords, Telegram tokens, Grafana passwords, or Kubernetes secrets.
- Testcontainers integration tests provide their own temporary PostgreSQL/Kafka connection settings through test code.

## Observability and DevOps

- Spring Boot actuator health/readiness/liveness and Prometheus metrics.
- Custom metrics for reminder delivery, Telegram attempts, Kafka outbox scans/results, Kafka publish/consume behavior, and receipt persistence.
- Local Prometheus and Grafana provisioning under `observability/`.
- Incident-oriented metric guide, PromQL examples, and alert examples in [docs/observability.md](docs/observability.md).
- Docker image with non-root runtime user and `/tmp` as the writable path.
- Kubernetes manifests with probes, resource settings, security context, service, ingress, PDB, and local/prod overlays.
- CI pipeline validates Compose files, runs Gradle build/tests, builds and smoke-tests the image, scans with Trivy, generates an SBOM, signs the image, and creates/verifies attestations.
- Config/secrets story is intentionally simple: local `.env`, GitHub Actions secrets for CI/deploy, and documented external secret manager recommendation for real Kubernetes production.

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
