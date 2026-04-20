# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Copy Gradle wrapper and dependency metadata first so Docker can cache this layer.
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
RUN chmod +x gradlew

# Pre-fetch dependencies (cached unless build files change).
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy source and build the fat JAR (skip tests — run them in CI, not here).
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ─── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Render (and most cloud platforms) route external traffic to port 8080.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
