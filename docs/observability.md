# Observability Baseline

## What is instrumented

The repository contains a real observability baseline for local and deployment-time diagnostics:

- Spring Boot actuator health endpoints
- Prometheus scrape endpoint at `/actuator/prometheus`
- Micrometer metrics for HTTP, JVM, and custom reminder/Kafka flows
- Prometheus config in `observability/prometheus/prometheus.yml`
- Grafana provisioning in `observability/grafana/provisioning`
- provisioned dashboard in `observability/grafana/dashboards/todo-observability.json`

## Endpoint exposure by profile

`application.yml` enables health probes and Prometheus export. Profile-specific files control what is exposed over HTTP:

- `dev`
  `health`, `info`, `metrics`, `prometheus`
- `prod`
  `health`, `info`, `prometheus`
- `test`
  `health`, `info`, `metrics`, `prometheus`

These can be overridden through:

- `TODO_OBS_ENDPOINTS_WEB_EXPOSURE_INCLUDE`
- `TODO_OBS_HEALTH_SHOW_COMPONENTS`
- `TODO_OBS_HEALTH_SHOW_DETAILS`

## Local stack

Start the full local stack:

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

Then use:

- app health: `http://localhost:8080/actuator/health`
- readiness: `http://localhost:8080/actuator/health/readiness`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000`

Grafana credentials come from `.env`:

- user: `${TODO_GRAFANA_ADMIN_USER:-admin}`
- password: `${TODO_GRAFANA_ADMIN_PASSWORD:-admin}`

## What Prometheus scrapes

`observability/prometheus/prometheus.yml` configures two scrape jobs:

- `prometheus`
- `todo-app`

The application is scraped from:

- target: `app:8080`
- path: `/actuator/prometheus`

## Provisioned Grafana assets

Grafana provisioning is file-based:

- datasource: `observability/grafana/provisioning/datasources/datasource.yml`
- dashboard provider: `observability/grafana/provisioning/dashboards/dashboard.yml`
- dashboard JSON: `observability/grafana/dashboards/todo-observability.json`

Current dashboard title:

- `ToDo App Observability Baseline`

Current dashboard focus:

- scrape availability
- request throughput
- HTTP latency percentiles
- HTTP error rate
- JVM heap usage
- CPU usage
- JVM threads
- process uptime

## Custom application metrics

Kafka scheduling metrics:

- `todo.reminder.scheduled.events.published`
- `todo.reminder.scheduled.events.publish.failures`
- `todo.reminder.scheduled.events.publish.duration`
- `todo.reminder.scheduled.events.consumed`
- `todo.reminder.scheduled.events.retries`
- `todo.reminder.scheduled.events.failed`
- `todo.reminder.scheduled.event.consume.lag`

Reminder delivery / Telegram metrics:

- `todo.reminder.delivery.attempts`
- `todo.reminder.delivery.attempt.duration`
- `todo.reminder.delivery.retries`
- `todo.reminder.delivery.scans`
- `todo.reminder.delivery.delivered`

Standard framework metrics also exist through Micrometer, including HTTP server metrics and JVM/process metrics.

## How to use the baseline for diagnostics

When the app looks unhealthy, follow this order:

1. Check `/actuator/health` and `/actuator/health/readiness`.
2. Check recent app logs.
3. Inspect `/actuator/prometheus` for reminder and Kafka metrics.
4. Open Prometheus target status to verify that `todo-app` is being scraped.
5. Open the Grafana dashboard to correlate HTTP behavior, JVM state, and process health.

Useful local commands:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_reminder|http_server_requests'
docker compose logs app --tail=200
```

## Limits of the current baseline

The repository does not currently include:

- distributed tracing
- centralized log aggregation
- alertmanager rules or alert routing
- SLO/error-budget automation
- cluster-level Prometheus/Grafana manifests

This is an operational baseline for visibility, not a full production observability platform.
