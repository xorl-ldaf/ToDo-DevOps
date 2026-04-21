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
- `TODO_TELEGRAM_*` for Telegram outbound reminder delivery
- `TODO_REMINDER_DELIVERY_*` for the due-reminder scan loop that drives outbound delivery
- `TODO_OBS_*` for actuator and observability tuning
- `TODO_GRAFANA_*` for local Grafana credentials
- `TODO_DB_URL` is an optional full JDBC override; otherwise the app composes the JDBC URL from `TODO_DB_HOST`, `TODO_DB_PORT`, and `TODO_DB_NAME`
- `TODO_KAFKA_BOOTSTRAP_SERVERS` is the broker address used when Kafka integration is enabled
- secrets are values like `TODO_DB_PASSWORD` and `TODO_GRAFANA_ADMIN_PASSWORD`; keep them only in local `.env` files or external secret stores, never in committed config

Required vs optional inputs:

- always safe to leave unset for local `dev`: `TODO_APP_NAME`, `TODO_APP_SERVER_PORT`, `TODO_DB_HOST`, `TODO_DB_PORT`, `TODO_DB_NAME`, `TODO_DB_USERNAME`, `TODO_GRAFANA_ADMIN_USER`
- always safe to leave unset for local `dev`: `TODO_KAFKA_ENABLED`, `TODO_KAFKA_BOOTSTRAP_SERVERS`, `TODO_KAFKA_TOPIC_REMINDER_SCHEDULED_V1`, `TODO_KAFKA_CONSUMER_GROUP_ID`
- always safe to leave unset for local `dev`: `TODO_TELEGRAM_ENABLED`, `TODO_TELEGRAM_BASE_URL`, `TODO_REMINDER_DELIVERY_ENABLED`, `TODO_REMINDER_DELIVERY_INITIAL_DELAY_MS`, `TODO_REMINDER_DELIVERY_FIXED_DELAY_MS`
- optional hardening overrides: `TODO_TELEGRAM_CONNECT_TIMEOUT`, `TODO_TELEGRAM_READ_TIMEOUT`, `TODO_TELEGRAM_MAX_ATTEMPTS`, `TODO_TELEGRAM_RETRY_BACKOFF`
- optional hardening overrides: `TODO_KAFKA_PRODUCER_RETRIES`, `TODO_KAFKA_PRODUCER_RETRY_BACKOFF`, `TODO_KAFKA_PRODUCER_REQUEST_TIMEOUT`, `TODO_KAFKA_PRODUCER_DELIVERY_TIMEOUT`, `TODO_KAFKA_CONSUMER_MAX_ATTEMPTS`, `TODO_KAFKA_CONSUMER_RETRY_BACKOFF`
- optional runtime hardening overrides: `TODO_REMINDER_DELIVERY_BATCH_SIZE`, `TODO_APP_SHUTDOWN_TIMEOUT`
- required secrets for any real environment and should come from the outside: `TODO_DB_PASSWORD`, `TODO_GRAFANA_ADMIN_PASSWORD`
- required secret when `TODO_TELEGRAM_ENABLED=true`: `TODO_TELEGRAM_BOT_TOKEN`
- required for `prod` runs: `TODO_DB_USERNAME` and either `TODO_DB_URL` or the full `TODO_DB_HOST` + `TODO_DB_PORT` + `TODO_DB_NAME` set
- optional observability overrides: `TODO_OBS_ENDPOINTS_WEB_EXPOSURE_INCLUDE`, `TODO_OBS_HEALTH_SHOW_COMPONENTS`, `TODO_OBS_HEALTH_SHOW_DETAILS`

Defaults are safe where possible:

- shared config keeps only common settings
- `prod` requires external database connection values, hides actuator component/detail data by default, and does not expose `/actuator/metrics`
- `dev` and `test` keep richer actuator visibility for local debugging and automated checks

## Telegram reminder delivery

Roadmap item 18 is implemented as a real outbound adapter, not as a controller-side bot stub:

- due reminders are scanned by the application runtime and converted into an explicit application contract `ReminderNotificationV1`
- the application layer depends on the outbound port `DeliverReminderNotificationPort`
- the Telegram HTTP integration lives in `adapters/out/messaging-telegram`
- if Telegram delivery is disabled, the runtime stays healthy and reminders remain `PENDING`
- reminders are marked `PUBLISHED` only after the outbound adapter reports a successful delivery

Current recipient strategy:

- bot credentials come from environment variables
- recipient chat routing comes from the existing user field `telegramChatId`
- if a due reminder resolves to a user without `telegramChatId`, the reminder is skipped and stays pending
- if Telegram is enabled without `TODO_TELEGRAM_BOT_TOKEN`, the app fails fast on startup because that is a real misconfiguration

Enable it with:

```bash
export TODO_TELEGRAM_ENABLED=true
export TODO_TELEGRAM_BOT_TOKEN=<telegram-bot-token>
export TODO_REMINDER_DELIVERY_ENABLED=true
```

Optional overrides:

- `TODO_TELEGRAM_BASE_URL` for stub tests or proxying; default is `https://api.telegram.org`
- `TODO_TELEGRAM_CONNECT_TIMEOUT` and `TODO_TELEGRAM_READ_TIMEOUT` for explicit outbound HTTP timeout boundaries
- `TODO_TELEGRAM_MAX_ATTEMPTS` and `TODO_TELEGRAM_RETRY_BACKOFF` for bounded retry discipline on transient Telegram failures
- `TODO_REMINDER_DELIVERY_INITIAL_DELAY_MS` for scheduler startup delay
- `TODO_REMINDER_DELIVERY_FIXED_DELAY_MS` for the due-reminder scan cadence
- `TODO_REMINDER_DELIVERY_BATCH_SIZE` for the maximum number of due reminders claimed per scan transaction

Delivery contract baseline:

- notification type/version: `reminder.notification` / `v1`
- required fields: reminder id, task id, task title, remind-at timestamp, recipient user id, recipient display name, recipient Telegram chat id
- message text is formatted inside the Telegram adapter from the structured contract; Telegram API details do not leak into controllers or domain code

Production hardening baseline for reminder delivery:

- Telegram delivery now uses explicit connect/read timeouts instead of unbounded waits
- retries are limited to transient transport errors, HTTP `429`, and `5xx`; permanent `4xx` failures are not retried
- due-reminder scans claim pending reminders through a database transaction with `FOR UPDATE SKIP LOCKED`, which prevents duplicate pickup across overlapping scheduler runs and multiple pods
- permanent delivery failures move reminders to `FAILED`; transient failures keep them `PENDING` for a later scan
- metrics expose delivery attempts, retries, scan outcomes, and Kafka publish/consume failures so failures are visible instead of silent

Minimal local verification flow:

1. Create a user with `telegramChatId`.
2. Create a task for that user.
3. Create a reminder in the future.
4. Run the app with Telegram delivery enabled.
5. Wait for the due-reminder scan loop to call the Telegram Bot API `sendMessage`.

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
- Telegram reminder delivery uses the separate due-reminder scan loop and does not depend on the Kafka scheduling topic
- Production-hardening signals add `todo.reminder.delivery.attempts`, `todo.reminder.delivery.retries`, `todo.reminder.delivery.scans`, `todo.reminder.delivery.delivered`, `todo.reminder.scheduled.events.publish.failures`, and `todo.reminder.scheduled.events.failed`

## Kubernetes baseline

Kubernetes manifests are tracked separately from the Docker Compose flow under `deploy/k8s/`.

- Kustomize base: `deploy/k8s/base`
- Local overlay: `deploy/k8s/overlays/local`
- Prod overlay: `deploy/k8s/overlays/prod`

This baseline deploys only the runtime web application workload. PostgreSQL, Kafka, Prometheus, and Grafana stay outside the cluster scope for this roadmap step and must be provided as external dependencies through the overlay `ConfigMap` and `Secret` inputs.

See [deploy/k8s/README.md](/home/honeybadger/itmo/ToDo-DevOps/deploy/k8s/README.md) for render/apply/check commands and the expected image/tag and environment configuration workflow.

## Production hardening baseline

Roadmap item 19 is implemented as a baseline hardening step on top of the existing reminder, Kafka, and Kubernetes foundations.

What is hardened:

- Telegram outbound delivery now has explicit timeout boundaries and bounded retry behavior
- reminder background processing now runs inside a transaction and claims due reminders with row locking to avoid duplicate concurrent pickup
- permanent Telegram delivery failures stop retry storms by transitioning reminders to `FAILED`
- Kafka producer/consumer configuration now has explicit retry/backoff/timeouts and failure visibility metrics
- Kubernetes deployment defaults now include rolling-update guardrails plus graceful shutdown budget

What remains intentionally out of scope:

- no outbox / transactional saga platform
- no exactly-once guarantee against every external side effect boundary
- no dead-letter-topic ecosystem for the current Kafka baseline
- no HPA / PDB / NetworkPolicy platform expansion in Kubernetes

For delivery/CD, the repository now keeps deploy automation separate from CI:

- `CI` publishes the image and uploads a `published-image-metadata` artifact with the immutable digest reference
- `.github/workflows/deploy.yaml` performs a manual Kubernetes deployment by consuming that artifact, rendering the existing Kustomize overlay with the exact digest, and verifying rollout plus application health
- GitHub Environments are the intended place for deployment-scoped kubeconfig secrets and optional namespace overrides

## Test profile

`test` is implemented in `apps/web-app/src/test/resources/application-test.yml` on purpose. It keeps test-only actuator defaults alongside the integration tests, is activated by `@ActiveProfiles("test")`, and is not shipped in the runtime artifact.
