# Observability Baseline

## What is instrumented

The repository contains a real observability baseline for local and deployment-time diagnostics:

- Spring Boot actuator health endpoints
- Prometheus scrape endpoint at `/actuator/prometheus`
- Micrometer metrics for HTTP/JVM/process
- project-specific metrics for reminder delivery, Telegram outcomes, Kafka outbox activity, Kafka publish/consume failures, and receipt audit
- Prometheus config in `observability/prometheus/prometheus.yml`
- Grafana provisioning in `observability/grafana/provisioning`
- provisioned dashboard in `observability/grafana/dashboards/todo-observability.json`

## Endpoint exposure by profile

`application.yml` enables health probes and Prometheus export. Profile-specific files control HTTP exposure:

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

## What the dashboard answers

The current dashboard is intentionally project-specific. It is meant to answer:

- Is the app up and responding?
- Are reminder scans succeeding or failing?
- Are reminders being delivered, retried, or failing terminally?
- How is Telegram behaving by outcome and latency?
- Is the Kafka outbox publishing or building retry pressure?
- Are Kafka consume lag and receipt persistence healthy?

## Custom application metrics

### Reminder processing

- `todo.reminder.delivery.scans`
- `todo.reminder.delivery.claimed`
- `todo.reminder.delivery.results`
- `todo.reminder.delivery.attempts`
- `todo.reminder.delivery.attempt.duration`

Interpretation:

- `scans{outcome=success|failure}` shows scheduler scan execution
- `claimed` shows reminders claimed for processing
- `results{outcome=delivered|retried|failed|conflict}` shows finalize outcomes
- `attempts{outcome=delivered|retryable_failure|permanent_failure}` shows Telegram adapter classifications
- `attempt.duration` shows external Telegram call duration by outcome

### Kafka outbox

- `todo.kafka.outbox.scans`
- `todo.kafka.outbox.claimed`
- `todo.kafka.outbox.results`

Interpretation:

- `scans{outcome=success|failure}` shows outbox worker passes
- `claimed` shows outbox rows claimed for publication
- `results{outcome=published|retried|failed|conflict}` shows finalize outcomes

### Kafka publish / consume / receipt audit

- `todo.reminder.scheduled.events.published`
- `todo.reminder.scheduled.events.publish.failures`
- `todo.reminder.scheduled.events.publish.duration`
- `todo.reminder.scheduled.events.consumed`
- `todo.reminder.scheduled.events.retries`
- `todo.reminder.scheduled.events.failed`
- `todo.reminder.scheduled.event.consume.lag`
- `todo.reminder.scheduled.events.receipts`

Interpretation:

- publish metrics describe the outbound Kafka boundary
- consume metrics describe listener behavior
- receipt metrics distinguish new receipt rows from duplicate event deliveries
- consume lag is the observed delay between event occurrence and local consumption

## How to use the baseline for diagnostics

When the app looks unhealthy, follow this order:

1. Check `/actuator/health` and `/actuator/health/readiness`.
2. Check recent app logs.
3. Inspect `/actuator/prometheus` for reminder and Kafka metrics.
4. Open Prometheus target status to verify that `todo-app` is being scraped.
5. Open the Grafana dashboard to correlate HTTP behavior, reminder worker behavior, Kafka outbox pressure, and JVM state.

Useful local commands:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_reminder|todo_kafka_outbox|http_server_requests'
docker compose logs app --tail=200
```

## Limits of the current baseline

The repository does not currently include:

- distributed tracing
- centralized log aggregation
- alertmanager rules or alert routing
- SLO/error-budget automation
- cluster-level Prometheus/Grafana manifests

This is a meaningful operational baseline for this project’s actual runtime paths, not a full platform observability stack.
