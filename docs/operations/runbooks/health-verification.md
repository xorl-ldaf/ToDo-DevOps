# Runbook: Health Verification

## Goal

Confirm that the application and its local operational baseline are healthy after startup or after a configuration change.

## Application health

Check the actuator endpoints:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/api/users
```

Expected result:

- health status is `UP`
- readiness status is `UP`
- `/api/users` returns a JSON array, even if it is empty

## Compose-level checks

If you are using Docker Compose:

```bash
docker compose ps
docker compose logs app --tail=100
```

Check that:

- `db` is healthy
- `kafka` is healthy
- `app` is running
- `prometheus` and `grafana` are running if you started the full stack

## Prometheus check

Open Prometheus:

- `http://localhost:9090`

Confirm:

- target `todo-app` is `UP`
- queries return data for `http_server_requests_seconds_count`

Quick command-line check:

```bash
curl --fail http://localhost:8080/actuator/prometheus | rg 'http_server_requests|todo_reminder'
```

## Grafana check

Open Grafana:

- `http://localhost:3000`

Confirm:

- datasource `Prometheus` exists
- dashboard `ToDo App Observability Baseline` is present

## Kubernetes-specific health check

If the workload is running in Kubernetes:

```bash
kubectl -n <namespace> rollout status deployment/todo-web-app --timeout=240s
kubectl -n <namespace> get deployment,pod,service,ingress
kubectl -n <namespace> port-forward service/todo-web-app 18080:80
curl --fail http://127.0.0.1:18080/actuator/health/readiness
curl --fail http://127.0.0.1:18080/api/users
```

Use the port-forwarded checks as the baseline truth even if ingress is not reachable yet.
