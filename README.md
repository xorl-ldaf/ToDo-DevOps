# ToDo-DevOps

## Local run

### Start application and database
```bash
docker compose up --build
```

### Observability baseline
```bash
docker compose up -d
```

- App health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000` with `${GRAFANA_ADMIN_USER:-admin}` / `${GRAFANA_ADMIN_PASSWORD:-admin}`
- Provisioned dashboard: `ToDo / ToDo App Observability Baseline`
