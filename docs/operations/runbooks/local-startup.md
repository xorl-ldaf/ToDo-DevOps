# Runbook: Local Startup

## Goal

Start the repository locally in a way that matches the current checkout and verify that the runtime is usable.

## Path A: full local stack with Docker Compose

1. Create local configuration:

   ```bash
   cp .env.example .env
   ```

2. Review `.env` and adjust only if needed:

- `TODO_DB_PASSWORD`
- `TODO_GRAFANA_ADMIN_PASSWORD`
- optional Kafka overrides
- optional Telegram overrides

1. Start the stack:

   ```bash
   docker compose --env-file .env up --build -d
   ```

1. Confirm service state:

   ```bash
   docker compose ps
   ```

1. Verify the application:

   ```bash
   curl --fail http://localhost:8080/actuator/health
   curl --fail http://localhost:8080/api/users
   ```

1. Verify observability services if needed:

   ```bash
   curl --fail http://localhost:9090/-/healthy
   curl --fail http://localhost:3000/api/health
   ```

Expected result:

- `db`, `kafka`, `app`, `prometheus`, and `grafana` are running
- `/actuator/health` returns `UP`
- `/api/users` returns a JSON array

## Path B: direct app run in `dev`

Use this path when you do not want Prometheus or Grafana locally and you can provide PostgreSQL yourself.

1. Ensure PostgreSQL is reachable.
2. Export database variables if the defaults do not match your local DB.
3. Start the app:

   ```bash
   SPRING_PROFILES_ACTIVE=dev ./gradlew :apps:web-app:bootRun
   ```

4. Verify:

   ```bash
   curl --fail http://localhost:8080/actuator/health
   curl --fail http://localhost:8080/api/users
   ```

Kafka stays disabled in direct `dev` runs unless you explicitly enable it with `TODO_KAFKA_ENABLED=true` and provide a reachable bootstrap server.

## Path C: direct app run in `prod`

Use this when you want to test production-oriented config behavior locally.

```bash
SPRING_PROFILES_ACTIVE=prod \
TODO_DB_HOST=localhost \
TODO_DB_PORT=5432 \
TODO_DB_NAME=todo \
TODO_DB_USERNAME=postgres \
TODO_DB_PASSWORD=postgres \
./gradlew :apps:web-app:bootRun
```

Expected difference from `dev`:

- actuator exposure is more restrictive by default
- database values must come from the environment

## Shutdown

Compose stack:

```bash
docker compose down -v
```

Direct app run:

- stop the Gradle process with `Ctrl+C`
