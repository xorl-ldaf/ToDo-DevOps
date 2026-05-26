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

Health endpoint intent:

- `/actuator/health` is the broad application health endpoint.
- `/actuator/health/readiness` is used by Docker Compose and Kubernetes readiness checks.
- `/actuator/health/liveness` is used by Kubernetes liveness checks.
- `/actuator/prometheus` is the Prometheus scrape endpoint.

## What the dashboard answers

The current dashboard is intentionally project-specific. It is meant to answer:

- Is the app up and responding?
- Are reminder scans succeeding or failing?
- Are reminders being delivered, retried, or failing terminally?
- How is Telegram behaving by outcome and latency?
- Is the Kafka outbox publishing or building retry pressure?
- Are Kafka consume lag and receipt persistence healthy?

## Custom application metrics

Micrometer metric names use dotted form in code, for example `todo.reminder.delivery.results`.
Prometheus exposes them with underscores and type suffixes, for example `todo_reminder_delivery_results_total`.

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
- `attempts{outcome=delivered|retryable_failure|non_retryable_failure}` shows Telegram adapter classifications
- `attempt.duration` shows external Telegram call duration by outcome

Incident signals:

- `todo_reminder_delivery_scans_total{outcome="failure"}` increasing means the scheduler itself is failing before or during processing.
- `todo_reminder_delivery_results_total{outcome="failed"}` increasing means reminders reached terminal failure after policy evaluation.
- `todo_reminder_delivery_results_total{outcome="retried"}` increasing with few `delivered` results means downstream delivery is unstable.
- `todo_reminder_delivery_results_total{outcome="conflict"}` increasing suggests concurrent workers or stale processing ownership.

### Kafka outbox

- `todo.kafka.outbox.scans`
- `todo.kafka.outbox.claimed`
- `todo.kafka.outbox.results`

Interpretation:

- `scans{outcome=success|failure}` shows outbox worker passes
- `claimed` shows outbox rows claimed for publication
- `results{outcome=published|retried|failed|conflict}` shows finalize outcomes

Incident signals:

- `todo_kafka_outbox_scans_total{outcome="failure"}` increasing means the outbox worker is failing as a unit.
- `todo_kafka_outbox_results_total{outcome="retried"}` increasing usually points to Kafka publication instability.
- `todo_kafka_outbox_results_total{outcome="failed"}` increasing means outbox rows exhausted retry policy.
- `todo_kafka_outbox_claimed_total` increasing while `published` stays flat means events are being picked up but not successfully published.

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

Incident signals:

- `todo_reminder_scheduled_events_publish_failures_total` increasing means the Kafka publisher failed before the event was accepted.
- `todo_reminder_scheduled_events_failed_total` increasing means the Kafka consumer exhausted retries.
- `todo_reminder_scheduled_events_receipts_total{outcome="duplicate"}` increasing means duplicate Kafka deliveries are occurring; this is expected occasionally in at-least-once delivery, but sustained growth deserves investigation.
- `todo_reminder_scheduled_event_consume_lag_seconds_*` rising means the consumer is falling behind event production or was offline.

## PromQL examples

Basic availability and HTTP:

```promql
max(up{job="todo-app"})
sum(rate(http_server_requests_seconds_count{job="todo-app", uri!~"/actuator.*"}[5m]))
sum(rate(http_server_requests_seconds_count{job="todo-app", status=~"5..", uri!~"/actuator.*"}[5m]))
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="todo-app", uri!~"/actuator.*"}[5m])))
```

Reminder delivery:

```promql
sum(increase(todo_reminder_delivery_scans_total{job="todo-app", outcome="failure"}[15m]))
sum(rate(todo_reminder_delivery_results_total{job="todo-app"}[5m])) by (outcome)
sum(rate(todo_reminder_delivery_attempts_total{job="todo-app", channel="telegram"}[5m])) by (outcome)
sum(rate(todo_reminder_delivery_attempt_duration_seconds_sum{job="todo-app", channel="telegram"}[5m])) by (outcome)
/
clamp_min(sum(rate(todo_reminder_delivery_attempt_duration_seconds_count{job="todo-app", channel="telegram"}[5m])) by (outcome), 1)
```

Kafka outbox and event flow:

```promql
sum(rate(todo_kafka_outbox_results_total{job="todo-app"}[5m])) by (outcome)
sum(increase(todo_kafka_outbox_results_total{job="todo-app", outcome="failed"}[15m]))
sum(rate(todo_reminder_scheduled_events_publish_failures_total{job="todo-app"}[5m])) by (reason)
sum(rate(todo_reminder_scheduled_events_consumed_total{job="todo-app"}[5m])) by (event_version)
sum(rate(todo_reminder_scheduled_events_receipts_total{job="todo-app"}[5m])) by (outcome)
sum(rate(todo_reminder_scheduled_event_consume_lag_seconds_sum{job="todo-app"}[5m]))
/
clamp_min(sum(rate(todo_reminder_scheduled_event_consume_lag_seconds_count{job="todo-app"}[5m])), 1)
```

Receipt duplicates:

```promql
sum(increase(todo_reminder_scheduled_events_receipts_total{job="todo-app", outcome="duplicate"}[1h]))
sum(rate(todo_reminder_scheduled_events_receipts_total{job="todo-app", outcome="duplicate"}[5m]))
/
clamp_min(sum(rate(todo_reminder_scheduled_events_receipts_total{job="todo-app"}[5m])), 1)
```

## Alert examples

These examples document useful alerting intent. The repository does not ship an Alertmanager setup or production routing.

```yaml
groups:
  - name: todo-app
    rules:
      - alert: TodoAppNotScraped
        expr: max(up{job="todo-app"}) != 1
        for: 2m
        labels:
          severity: page
        annotations:
          summary: todo-app is not being scraped by Prometheus
          description: Check container health, network reachability, and the /actuator/prometheus endpoint.

      - alert: TodoReminderDeliveryFailures
        expr: sum(increase(todo_reminder_delivery_results_total{job="todo-app", outcome="failed"}[15m])) > 0
        for: 5m
        labels:
          severity: ticket
        annotations:
          summary: Reminders are reaching terminal delivery failure
          description: Check Telegram configuration, user telegramChatId values, and reminder delivery logs.

      - alert: TodoTelegramRetryPressure
        expr: sum(rate(todo_reminder_delivery_attempts_total{job="todo-app", channel="telegram", outcome="retryable_failure"}[10m])) > 0
        for: 10m
        labels:
          severity: ticket
        annotations:
          summary: Telegram delivery has sustained retryable failures
          description: Check Telegram API availability, network timeouts, and rate limiting.

      - alert: TodoKafkaOutboxPublicationFailures
        expr: sum(increase(todo_kafka_outbox_results_total{job="todo-app", outcome=~"retried|failed"}[15m])) > 0
        for: 5m
        labels:
          severity: ticket
        annotations:
          summary: Kafka outbox publication is failing or retrying
          description: Check Kafka broker health, topic availability, and publisher errors.

      - alert: TodoKafkaReceiptDuplicatesHigh
        expr: |
          sum(rate(todo_reminder_scheduled_events_receipts_total{job="todo-app", outcome="duplicate"}[10m]))
          /
          clamp_min(sum(rate(todo_reminder_scheduled_events_receipts_total{job="todo-app"}[10m])), 1) > 0.2
        for: 15m
        labels:
          severity: ticket
        annotations:
          summary: Kafka receipt duplicate ratio is high
          description: At-least-once delivery allows duplicates, but sustained high duplicate ratio may indicate consumer restarts or offset instability.
```

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
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/actuator/health/liveness
curl --fail http://localhost:8080/actuator/prometheus | rg 'todo_reminder|todo_kafka_outbox|http_server_requests'
docker compose logs app --tail=200
```

Incident triage shortcuts:

- App is not reachable: check `up{job="todo-app"}`, `/actuator/health/readiness`, and container logs.
- Reminders are late: compare `claimed`, `delivered`, `retried`, and `failed` reminder counters.
- Telegram delivery is failing: inspect `todo_reminder_delivery_attempts_total{channel="telegram"}` by `outcome`.
- Kafka publication is unhealthy: inspect outbox `claimed`, `published`, `retried`, and `failed` counters.
- Kafka consumer behavior is suspicious: inspect consume lag plus receipt `stored` vs `duplicate` outcomes.

## Limits of the current baseline

The repository does not currently include:

- distributed tracing
- centralized log aggregation such as ELK or Loki
- committed Alertmanager routing or notification receivers
- SLO/error-budget automation
- cluster-level Prometheus/Grafana manifests

This is a meaningful operational baseline for this project's actual runtime paths, not a full platform observability stack.
