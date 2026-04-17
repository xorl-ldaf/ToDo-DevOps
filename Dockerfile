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
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/apps/web-app/build/libs/*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]