# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy Gradle wrapper and dependency descriptors first for layer caching
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties ./

# Download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon

# Copy source code and build the fat JAR, skipping tests
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Run stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy only the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Render injects PORT at runtime; Spring Boot listens on SERVER_PORT or 8080
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
