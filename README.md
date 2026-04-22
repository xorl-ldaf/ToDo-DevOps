# ToDo-DevOps

ToDo-DevOps is a portfolio project built around a Java/Spring Boot application with a deliberate DevOps focus. The codebase keeps domain and application logic inside `core/*`, isolates infrastructure behind ports/adapters, and treats delivery, observability, and deployment concerns as first-class engineering concerns instead of postscript tooling.

## What is implemented

- REST API for users, tasks, and task reminders.
- PostgreSQL persistence with Flyway migrations.
- Reminder lifecycle with explicit states: `SCHEDULED`, `PROCESSING`, `DELIVERED`, `FAILED`.
- Reminder delivery worker with short DB transactions, claim/process/finalize stages, retry scheduling, and lease-based concurrency control.
- Kafka `ReminderScheduledEventV1` integration with an outbox-backed publication contract.
- Kafka consumer path with idempotent receipt persistence for audit/reconciliation.
- Telegram outbound adapter with timeout discipline and single-attempt failure classification.
- Docker Compose local stack with Postgres, Kafka, Prometheus, and Grafana.
- Kubernetes manifests with Kustomize overlays, pod hardening baseline, PDB, and placement hints.
- GitHub Actions CI plus a separate manual deploy workflow.
- Image scanning, SBOM generation, signing, and attestation verification.

## Architecture overview

```text
clients / curl / tests
        |
        v
adapters/in/web-rest        adapters/in/messaging-kafka
        |                              |
        +------------> application <---+
                           |
                         domain
                           |
        +------------------+------------------+-------------------+
        |                  |                  |                   |
        v                  v                  v                   v
adapters/out/persistence   adapters/out       adapters/out        apps/web-app
-jpa                       /messaging-kafka   /messaging-telegram runtime wiring
```

Module layout follows the actual Gradle build:

- `core/domain`
- `core/application`
- `adapters/in/web-rest`
- `adapters/in/messaging-kafka`
- `adapters/out/persistence-jpa`
- `adapters/out/messaging-kafka`
- `adapters/out/messaging-telegram`
- `apps/web-app`

More detail is in [docs/architecture.md](docs/architecture.md).

## Reminder contracts

### Reminder creation

`POST /api/tasks/{taskId}/reminders` returns success only after:

1. the reminder row is committed in PostgreSQL;
2. the matching `ReminderScheduledEventV1` is committed to the outbox when Kafka integration is enabled.

That means the API contract is no longer “saved in DB and best-effort Kafka publish maybe happened”. It is:

- reminder is durable in the primary store;
- if Kafka is enabled, the event is durable in the outbox and will be published asynchronously by the outbox worker;
- the request does not claim synchronous publication to Kafka.

### Reminder delivery

Due reminder processing is explicitly split into:

1. claim due reminders in a short transaction using `FOR UPDATE SKIP LOCKED`;
2. perform Telegram HTTP outside the transaction;
3. finalize as `DELIVERED`, reschedule for retry, or mark `FAILED` in a new short transaction.

The worker no longer keeps row locks open during external HTTP or backoff logic.

### Kafka role

Kafka is kept as a real integration boundary, but it is not the delivery execution path. Its role in this checkout is:

- publish durable reminder-scheduled integration events from the outbox;
- consume them through a separate adapter;
- persist idempotent receipt records for audit/reconciliation and metrics.

## Tech stack

- Java 21
- Spring Boot, Spring Web, Spring Data JPA, Spring Actuator, Spring Kafka
- PostgreSQL + Flyway
- Docker + Docker Compose
- Kubernetes + Kustomize
- Prometheus + Grafana
- GitHub Actions
- Trivy, Cosign, GitHub attestations

## Quick start

1. Create a local env file:

   ```bash
   cp .env.example .env
   ```

2. Start the local stack:

   ```bash
   docker compose --env-file .env up --build
   ```

3. Verify the baseline:

   ```bash
   curl --fail http://localhost:8080/actuator/health
   curl --fail http://localhost:8080/api/users
   ```

4. Open the local UIs:

- App: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

The default Compose stack runs the app with `SPRING_PROFILES_ACTIVE=dev`, uses the local Postgres and Kafka containers, enables Kafka integration for the app container, and leaves Telegram delivery disabled unless you opt in through `.env`.

## Local development

Run the app directly in `dev` mode against an external or separately started Postgres instance:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew :apps:web-app:bootRun
```

Run the app directly in `prod` mode with explicit database wiring:

```bash
SPRING_PROFILES_ACTIVE=prod \
TODO_DB_HOST=localhost \
TODO_DB_PORT=5432 \
TODO_DB_NAME=todo \
TODO_DB_USERNAME=postgres \
TODO_DB_PASSWORD=postgres \
./gradlew :apps:web-app:bootRun
```

## Tests and verification

Run the test suite:

```bash
./gradlew test
```

Build the application artifact:

```bash
./gradlew clean build
```

Validate Compose manifests:

```bash
docker compose -f compose.yaml config -q
docker compose -f compose.smoke.yaml config -q
```

Render Kubernetes overlays:

```bash
kubectl kustomize deploy/k8s/overlays/local
kubectl kustomize deploy/k8s/overlays/prod
```

## Observability, security, and deployment

- Observability baseline: Spring Boot actuator, Prometheus scrape config, Grafana provisioning, reminder/Kafka/Telegram-specific metrics, and a project-focused dashboard. See [docs/observability.md](docs/observability.md).
- Security / supply chain baseline: Trivy image scan, CycloneDX SBOM, Cosign signing, provenance attestation, SBOM attestation, non-root container runtime, and Kubernetes workload hardening. See [docs/security-supply-chain.md](docs/security-supply-chain.md).
- Deployment baseline: Kustomize overlays, digest-based rendering, and a manual GitHub Actions deploy workflow for Kubernetes. See [docs/deployment.md](docs/deployment.md).

## Documentation map

- [docs/architecture.md](docs/architecture.md)
- [docs/deployment.md](docs/deployment.md)
- [docs/observability.md](docs/observability.md)
- [docs/security-supply-chain.md](docs/security-supply-chain.md)
- [docs/operations/runbooks/local-startup.md](docs/operations/runbooks/local-startup.md)
- [docs/operations/runbooks/health-verification.md](docs/operations/runbooks/health-verification.md)
- [docs/operations/runbooks/failed-startup.md](docs/operations/runbooks/failed-startup.md)
- [docs/operations/runbooks/post-deploy-smoke.md](docs/operations/runbooks/post-deploy-smoke.md)
- [docs/operations/runbooks/rollback-first-checks.md](docs/operations/runbooks/rollback-first-checks.md)
- [docs/operations/runbooks/integration-troubleshooting.md](docs/operations/runbooks/integration-troubleshooting.md)

## Current boundaries

This checkout is materially stronger than a CRUD sample, but it still has explicit limits:

- no in-cluster PostgreSQL, Kafka, Prometheus, or Grafana deployment
- no tracing stack or centralized log aggregation
- no NetworkPolicy, HPA, or admission-controller policy set
- no secret-manager integration
- Telegram delivery remains at-least-once under crash-after-send-before-finalize failure modes because Telegram does not provide an idempotency key for this path
- Kafka receipt persistence is an audit/reconciliation boundary, not a business workflow of its own
