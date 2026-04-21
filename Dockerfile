# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && \
    ./gradlew clean bootJar --no-daemon -x test \
    -Dspring.datasource.url=jdbc:h2:mem:testdb \
    -Dspring.datasource.driver-class-name=org.h2.Driver \
    -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
    -Dspring.jpa.hibernate.ddl-auto=validate

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
