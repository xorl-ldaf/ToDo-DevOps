# Runbook: Troubleshooting Failed Startup

## Goal

Determine why the application did not start and fix the first broken dependency instead of guessing.

## First checks for Docker Compose

1. Inspect container state:

   ```bash
   docker compose ps
   ```

2. Read the app logs:

   ```bash
   docker compose logs app --tail=200
   ```

3. If Postgres is unhealthy, inspect DB logs:

   ```bash
   docker compose logs db --tail=200
   ```

4. If Kafka is unhealthy, inspect broker logs:

   ```bash
   docker compose logs kafka --tail=200
   ```

## First checks for direct `bootRun`

1. Confirm the active profile:

   ```bash
   echo "${SPRING_PROFILES_ACTIVE}"
   ```

2. Confirm database variables when using `prod`:

   ```bash
   env | rg '^TODO_DB_|^SPRING_PROFILES_ACTIVE'
   ```

3. Re-run with visible console output:

   ```bash
   SPRING_PROFILES_ACTIVE=dev ./gradlew :apps:web-app:bootRun
   ```

## Common real failure modes in this checkout

### Database configuration is missing or wrong

Symptoms:

- boot fails during datasource or Flyway initialization
- health never becomes `UP`

What to check:

- `TODO_DB_HOST`
- `TODO_DB_PORT`
- `TODO_DB_NAME`
- `TODO_DB_USERNAME`
- `TODO_DB_PASSWORD`
- `TODO_DB_URL` if you overrode the JDBC URL directly

### `prod` profile is active without explicit DB settings

Symptoms:

- the application starts with unresolved or invalid database connection settings

What to check:

- `SPRING_PROFILES_ACTIVE=prod` requires explicit DB environment input

### Kafka is enabled but the broker is unreachable

Symptoms:

- startup or reminder creation logs contain Kafka connection failures

What to check:

- `TODO_KAFKA_ENABLED=true`
- `TODO_KAFKA_BOOTSTRAP_SERVERS`
- broker container or external broker availability

### Telegram is enabled but the bot token is missing

Symptoms:

- startup fails fast in Telegram configuration

What to check:

- `TODO_TELEGRAM_ENABLED=true`
- `TODO_TELEGRAM_BOT_TOKEN` must be non-empty

### Port collision on the host

Symptoms:

- Compose fails to bind `5432`, `8080`, `9090`, `3000`, or Kafka host port `9094`

What to check:

- local processes already using those ports
- overrides in `.env`

## Kubernetes startup failures

1. Inspect rollout:

   ```bash
   kubectl -n <namespace> rollout status deployment/todo-web-app --timeout=240s
   ```

2. Describe the deployment and pod:

   ```bash
   kubectl -n <namespace> describe deployment todo-web-app
   kubectl -n <namespace> get pods
   kubectl -n <namespace> describe pod <pod-name>
   ```

3. Read logs:

   ```bash
   kubectl -n <namespace> logs deployment/todo-web-app --all-containers --tail=200
   ```

4. Re-check overlay config:

- `deploy/k8s/overlays/<env>/configmap.env`
- `deploy/k8s/overlays/<env>/secret.env`

Most Kubernetes startup failures in this repository will come from wrong external dependency endpoints or bad credentials rather than from missing manifests.
