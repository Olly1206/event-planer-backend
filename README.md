# Event Planner Backend Prototype

Spring Boot REST API for the Event Planner prototype. It provides authentication, event management, invite links, venue/vendor lookup, and weather forecast endpoints for the Android client.

## Prototype Scope

- User registration, login, JWT authentication, and guest mode
- Self-service account deletion for Play Store compliance
- Event creation, editing, deletion, search, created events, and joined events
- Participant join/leave flow
- Shareable invite tokens, short invite codes, public invite pages, and public legal pages
- Venue suggestions through OpenStreetMap/Nominatim/Overpass-style lookup
- Vendor suggestions based on selected event options
- Weather lookup through Open-Meteo, so no weather API key is required
- Swagger/OpenAPI UI via springdoc

## Tech Stack

- Java 25 toolchain
- Spring Boot 4
- Spring Web MVC, Spring Security, Spring Data JPA, Thymeleaf
- JWT authentication with `jjwt`
- MySQL by default, PostgreSQL/H2 drivers available
- Gradle wrapper
- Dockerfile with Eclipse Temurin 25

## Project Structure

```text
src/
|-- main/
|   |-- java/event_planer/project/
|   |   |-- config/       Spring, security, OpenAPI, cache, REST client config
|   |   |-- controller/   REST controllers and invite-page controller
|   |   |-- dto/          Request/response DTOs
|   |   |-- entity/       JPA entities
|   |   |-- exception/    Shared exception handling
|   |   |-- repository/   Spring Data repositories
|   |   |-- security/     JWT filter/service and security utilities
|   |   `-- service/      Business logic
|   `-- resources/
|       |-- application.properties
|       |-- application-local.properties
|       |-- application-build.properties
|       `-- templates/    Public invite/legal pages
`-- test/java/event_planer/project/
```

## Requirements

- JDK 25
- Gradle wrapper from this repository
- MySQL for the default profile, or H2 via the local profile

## Run Locally

For a quick prototype run without a local MySQL database:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The API starts at:

```text
http://localhost:8080
```

Swagger/OpenAPI UI is available after startup at:

```text
http://localhost:8080/swagger-ui.html
```

## Default Database Configuration

The default profile reads database credentials from environment variables and falls back to the values in `application.properties`:

```text
DB_URL=jdbc:mysql://localhost:3306/event_manager_db
DB_USERNAME=event_user
DB_PASSWORD=<password>
DB_DRIVER=com.mysql.cj.jdbc.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect
JWT_SECRET=<base64-secret>
```

Use environment variables for deployed or shared environments. The local profile uses in-memory H2 and is best for demos and smoke checks.

## Build And Test

```bash
./gradlew test
./gradlew bootJar
```

Docker build:

```bash
docker build -t event-planner-backend .
docker run -p 8080:8080 event-planner-backend
```

## Main API Endpoints

Authentication:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/guest`

Events:

- `GET /api/events`
- `POST /api/events`
- `GET /api/events/{id}`
- `PATCH /api/events/{id}`
- `DELETE /api/events/{id}`
- `GET /api/events/search?keyword=...`
- `GET /api/events/my`
- `GET /api/events/joined`
- `POST /api/events/{id}/join`
- `DELETE /api/events/{id}/leave`

Invite links and admin helpers:

- `GET /api/events/invite/{token}`
- `POST /api/events/invite/{token}/join`
- `GET /api/events/{id}/invite-link`
- `GET /api/events/{id}/invite-link/short`
- `POST /api/events/{id}/admins/{username}`
- `DELETE /api/events/{id}/admins/{adminUserId}`
- `GET /invite/{token}`, `GET /e/{token}`, `GET /s/{shortCode}`

Reference data and suggestions:

- `GET /api/events/types`
- `GET /api/events/options`
- `GET /api/venues?city=...&radiusMeters=...&locationType=...&eventType=...`
- `GET /api/vendors?city=...&radiusMeters=...&optionName=...`
- `GET /api/weather?city=...&date=...`
- `GET /api/weather/range?city=...&startDate=...&endDate=...`

Users:

- `GET /api/users/me`
- `DELETE /api/users/me`
- `GET /api/users/{id}`

Public legal pages:

- `GET /privacy`
- `GET /account-deletion`

## Android Client Integration

The Android app stores the JWT returned by register/login/guest calls and sends it as:

```text
Authorization: Bearer <token>
```

The current Android prototype points at the deployed Render backend by default through Gradle `BuildConfig`. For a local demo, update the Android `BASE_URL` build config to `http://10.0.2.2:8080/` for an emulator or `http://<computer-lan-ip>:8080/` for a physical phone.

## Packaging Notes

Do not include `.git/`, `.gradle/`, `build/`, `bin/`, `.idea/`, or local IDE files in stakeholder packages. The prototype handoff zip contains source files, Gradle wrappers, resources, and documentation only.
