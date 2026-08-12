# ToDo DevOps

## Running with Docker Compose

From the repository root, start the complete local stack:

```bash
docker compose up --build
```

Run it in the background:

```bash
docker compose up -d --build
```

Stop containers without deleting PostgreSQL data:

```bash
docker compose down
```

Fully reset local state, including the PostgreSQL volume:

```bash
docker compose down -v
```

The Compose stack starts:

- `postgres`: PostgreSQL with a persistent named volume.
- `kafka`: single-node Apache Kafka in KRaft mode.
- `web-app`: the Spring Boot application built from `apps:web-app`.

Application URL: <http://localhost:8080>

Health endpoint: <http://localhost:8080/actuator/health>

The application connects to PostgreSQL as `postgres:5432` and Kafka as `kafka:9092` inside the Compose network. Flyway migrations run automatically during application startup. Telegram delivery is disabled by default and can be enabled with environment variables, for example `TODO_TELEGRAM_ENABLED=true` and `TODO_TELEGRAM_BOT_TOKEN=...`, without rebuilding the image.
