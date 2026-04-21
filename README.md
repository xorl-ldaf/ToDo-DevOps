# ToDo-DevOps

ToDo-DevOps is a portfolio project built around a Java/Spring Boot application with a deliberate DevOps focus. The codebase follows onion/hexagonal structure, keeps business logic in `core/*`, isolates infrastructure behind ports and adapters, and adds operational baselines for packaging, observability, security, and deployment.

## What is implemented in this checkout

- REST API for users, tasks, and task reminders.
- PostgreSQL persistence with Flyway migrations.
- Kafka reminder scheduling integration behind explicit ports/adapters.
- Telegram reminder delivery as a real outbound adapter.
- Docker Compose local stack with Postgres, Kafka, Prometheus, and Grafana.
- Kubernetes manifests with `base`, `local`, and `prod` Kustomize overlays.
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
        +------------------+------------------+
        |                  |                  |
        v                  v                  v
adapters/out/persistence   adapters/out       adapters/out
-jpa                       /messaging-kafka   /messaging-telegram
                           |
                           v
                     external infrastructure

apps/web-app = Spring Boot wiring, runtime config, scheduler, bootstrapping
deploy/ + observability/ + .github/workflows/ = operational baseline
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

`prod` mode intentionally expects database settings from the environment. Health endpoint visibility is stricter there unless you override `TODO_OBS_*`.

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

## Observability, security, and deployment

- Observability baseline: Spring Boot actuator, Prometheus scrape config, Grafana provisioning, and a provisioned dashboard. See [docs/observability.md](docs/observability.md).
- Security / supply chain baseline: Trivy image scan, CycloneDX SBOM, Cosign signing, provenance attestation, SBOM attestation, and local verification script. See [docs/security-supply-chain.md](docs/security-supply-chain.md).
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

## Current status and boundaries

This local checkout contains working implementations for the post-item-14 baselines around configuration/environment handling, observability, Kafka integration, Kubernetes deployment manifests, delivery/CD automation, Telegram delivery, and production hardening. The repository itself does not store a numbered roadmap file, so exact item-to-number mapping must be inferred from local commit history rather than from an in-repo roadmap document.

The current baseline is intentionally limited:

- No in-cluster PostgreSQL, Kafka, Prometheus, or Grafana deployment.
- No outbox pattern or exactly-once guarantee across external boundaries.
- No centralized log aggregation or tracing stack.
- No HPA, PodDisruptionBudget, NetworkPolicy, or secret-manager integration.
- No claim of production-ready platform security beyond the implemented supply-chain controls.
