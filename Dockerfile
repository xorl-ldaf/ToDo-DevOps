# ---------- build stage ----------
# Tags are intentionally versioned but not digest-pinned here so local builds can
# receive base-image security refreshes. CI/deploy promotes the produced app image
# by immutable digest; pin base image digests if reproducible rebuilds become a
# stronger requirement than easy patch uptake.
FROM gradle:8.14.4-jdk21 AS build

WORKDIR /workspace

# Copy the full project because this is a multi-module Gradle build.
COPY . .

RUN ./gradlew :apps:web-app:bootJar --no-daemon \
    && mkdir -p /workspace/build/image \
    && JAR_COUNT="$(find /workspace/apps/web-app/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | wc -l)" \
    && test "${JAR_COUNT}" -eq 1 \
    && cp "$(find /workspace/apps/web-app/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar')" /workspace/build/image/app.jar

# ---------- runtime stage ----------
# See the build-stage note above for why this base image is tag-pinned rather
# than digest-pinned.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# curl is kept only for the OCI HEALTHCHECK. We avoid distroless here to keep
# local debugging straightforward while still running as a non-root user.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && groupadd --gid 10001 todo \
    && useradd --uid 10001 --gid 10001 --home-dir /app --no-create-home --shell /usr/sbin/nologin todo \
    && install -d -m 1777 /tmp \
    && chmod 0755 /app \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/image/app.jar /app/app.jar

RUN chmod 0555 /app \
    && chmod 0444 /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV TODO_APP_SERVER_PORT=8080
ENV JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp
ENV HOME=/tmp

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD sh -c 'curl -fsS "http://localhost:${TODO_APP_SERVER_PORT}/actuator/health/readiness" || exit 1'

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
