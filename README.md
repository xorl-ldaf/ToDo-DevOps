# ToDo-DevOps

## Configuration and environment

Runtime config is split into:

- `apps/web-app/src/main/resources/application.yml` for shared defaults
- `apps/web-app/src/main/resources/application-dev.yml` for local development defaults
- `apps/web-app/src/main/resources/application-prod.yml` for production-oriented safe defaults
- `apps/web-app/src/test/resources/application-test.yml` for automated test runtime behavior

Profile selection uses the standard Spring variable `SPRING_PROFILES_ACTIVE`:

- `dev` for local development and the default Docker Compose stack
- `prod` for production-like runs and the default container image profile
- `test` for Spring integration tests

Environment variable conventions:

- `TODO_APP_*` for application settings such as name and port
- `TODO_DB_*` for database connection settings
- `TODO_KAFKA_*` for Kafka bootstrap/topic/group settings
- `TODO_OBS_*` for actuator and observability tuning
- `TODO_GRAFANA_*` for local Grafana credentials
- `TODO_DB_URL` is an optional full JDBC override; otherwise the app composes the JDBC URL from `TODO_DB_HOST`, `TODO_DB_PORT`, and `TODO_DB_NAME`
- `TODO_KAFKA_BOOTSTRAP_SERVERS` is the broker address used when Kafka integration is enabled
- secrets are values like `TODO_DB_PASSWORD` and `TODO_GRAFANA_ADMIN_PASSWORD`; keep them only in local `.env` files or external secret stores, never in committed config

Required vs optional inputs:

- always safe to leave unset for local `dev`: `TODO_APP_NAME`, `TODO_APP_SERVER_PORT`, `TODO_DB_HOST`, `TODO_DB_PORT`, `TODO_DB_NAME`, `TODO_DB_USERNAME`, `TODO_GRAFANA_ADMIN_USER`
- always safe to leave unset for local `dev`: `TODO_KAFKA_ENABLED`, `TODO_KAFKA_BOOTSTRAP_SERVERS`, `TODO_KAFKA_TOPIC_REMINDER_SCHEDULED_V1`, `TODO_KAFKA_CONSUMER_GROUP_ID`
- required secrets for any real environment and should come from the outside: `TODO_DB_PASSWORD`, `TODO_GRAFANA_ADMIN_PASSWORD`
- required for `prod` runs: `TODO_DB_USERNAME` and either `TODO_DB_URL` or the full `TODO_DB_HOST` + `TODO_DB_PORT` + `TODO_DB_NAME` set
- optional observability overrides: `TODO_OBS_ENDPOINTS_WEB_EXPOSURE_INCLUDE`, `TODO_OBS_HEALTH_SHOW_COMPONENTS`, `TODO_OBS_HEALTH_SHOW_DETAILS`

Defaults are safe where possible:

- shared config keeps only common settings
- `prod` requires external database connection values, hides actuator component/detail data by default, and does not expose `/actuator/metrics`
- `dev` and `test` keep richer actuator visibility for local debugging and automated checks

## Local run

Create a local env file first:

```bash
cp .env.example .env
```

### Dev-like stack via Docker Compose

```bash
docker compose --env-file .env up --build
```

By default `.env.example` selects the `dev` profile, wires the app to the local Postgres container, starts a local Kafka broker, and reads placeholder values from `.env.example`. If you need a prod-like local run through Compose, override `SPRING_PROFILES_ACTIVE=prod` in your `.env` before starting the stack.

### Dev-like app run without Compose

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew :apps:web-app:bootRun
```

This expects a reachable Postgres instance and uses `TODO_DB_*` values from your shell environment or `.env`. Kafka stays disabled unless you explicitly export `TODO_KAFKA_ENABLED=true` and point `TODO_KAFKA_BOOTSTRAP_SERVERS` at a broker.

### Prod-like local app run

```bash
SPRING_PROFILES_ACTIVE=prod TODO_DB_HOST=localhost TODO_DB_PORT=5432 TODO_DB_NAME=todo TODO_DB_USERNAME=postgres TODO_DB_PASSWORD=postgres ./gradlew :apps:web-app:bootRun
```

The container image also defaults to `SPRING_PROFILES_ACTIVE=prod` unless explicitly overridden, and in that mode it expects database connection values to come from the environment rather than from baked-in production credentials.

Optional actuator overrides such as `TODO_OBS_HEALTH_SHOW_DETAILS` are available for direct app runs, but the default recommendation is to leave them unset and let the active profile choose the safe default behavior.

## Observability baseline

With the local Compose stack running:

- App health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Kafka broker for host-side tooling: `localhost:${TODO_KAFKA_HOST_PORT:-9094}`
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000` with `${TODO_GRAFANA_ADMIN_USER:-admin}` / `${TODO_GRAFANA_ADMIN_PASSWORD:-admin}`
- Provisioned dashboard: `ToDo / ToDo App Observability Baseline`
- Reminder scheduling Kafka flow emits/consumes `todo.reminder.scheduled.v1` by default and exposes `todo.reminder.scheduled.events.consumed` plus `todo.reminder.scheduled.event.consume.lag`

## Test profile

`test` is implemented in `apps/web-app/src/test/resources/application-test.yml` on purpose. It keeps test-only actuator defaults alongside the integration tests, is activated by `@ActiveProfiles("test")`, and is not shipped in the runtime artifact.
