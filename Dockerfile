FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY adapters ./adapters
COPY apps ./apps

RUN chmod +x ./gradlew \
    && ./gradlew :apps:web-app:bootJar --no-daemon \
    && jar_file="$(find apps/web-app/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' | sort | head -n 1)" \
    && test -n "${jar_file}" \
    && cp "${jar_file}" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S todo && adduser -S todo -G todo

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/app.jar

USER todo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
