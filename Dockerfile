# ---------- build stage ----------
FROM gradle:8.14.4-jdk21 AS build

WORKDIR /workspace

# Копируем весь проект и собираем только bootJar приложения
COPY . .

RUN gradle :apps:web-app:bootJar --no-daemon

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# curl нужен для HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && groupadd --gid 10001 todo \
    && useradd --uid 10001 --gid 10001 --home-dir /app --no-create-home --shell /usr/sbin/nologin todo \
    && mkdir -p /tmp \
    && chown -R 10001:10001 /app /tmp \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/apps/web-app/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV TODO_APP_SERVER_PORT=8080
ENV JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD sh -c 'curl -fsS "http://localhost:${TODO_APP_SERVER_PORT}/actuator/health/readiness" || exit 1'

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
