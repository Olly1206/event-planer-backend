# Event Planner Backend - Spring Boot API

A modern RESTful API backend for event planning and management, built with Spring Boot 4.x, Spring Security with JWT, and Spring Data JPA with MySQL.

## Overview

This is the backend server for the Event Planner application, providing comprehensive REST APIs for:
- **User Management**: Registration, authentication (JWT), and guest mode support
- **Event Management**: Create, update, delete, and retrieve events with filtering and search
- **Event Participants**: Join events, RSVP management, and participant tracking  
- **Admin Features**: Event administration, participant management, and admin role assignments
- **Invite Links**: Shareable invite tokens with public event previews
- **Venue & Vendor Integration**: Geolocation-based venue/vendor search using Overpass API
- **Guest Mode**: Temporary guest accounts that auto-expire after 30 days
- **Offline Caching**: ETag and Cache-Control headers for efficient client-side caching

## Quick Start

### Prerequisites
- Java 25
- Gradle 8.0+
- MySQL 8+

### Build & Run

```bash
# Build the project
./gradlew build

# Run the application (development)
./gradlew bootRun

# Run tests
./gradlew test

# Build production JAR
./gradlew bootJar
```

The API will be available at `http://localhost:8080`

## API Documentation

### Authentication Endpoints
- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login with email & password
- `POST /api/auth/guest` - Create temporary guest account (30-day expiration)

### Event Endpoints
- `GET /api/events` - Get all public events (with caching)
- `GET /api/events/{id}` - Get event details
- `POST /api/events` - Create new event (authenticated only)
- `PATCH /api/events/{id}` - Update event
- `DELETE /api/events/{id}` - Delete event
- `POST /api/events/{id}/join` - Join event (with optional participant name)
- `DELETE /api/events/{id}/leave` - Leave event
- `GET /api/events/my` - Get user's created events
- `GET /api/events/joined` - Get user's joined events

### Invite Link Endpoints
- `GET /api/events/invite/{token}` - Public event preview (no auth required)
- `POST /api/events/invite/{token}/join` - Join event via invite link (with optional participant name)
- `GET /api/events/{id}/invite-link` - Get event's invite token (organiser only)

### Venue Endpoints
- `GET /api/venues/search` - Search venues by location, filters
- `POST /api/venues/nearby` - Find nearby venues with geolocation

### Reference Data
- `GET /api/events/types` - Available event types
- `GET /api/events/options` - Available event options (catering, AV, etc.)

## Key Features

### Security
- JWT token-based authentication with Spring Security
- Password hashing with bcrypt
- Role-based access control (GUEST, PRIVATE, COMPANY)
- Private/public event visibility settings

### Guest Mode
- Temporary accounts without email/password
- 30-day auto-expiration with scheduled cleanup
- Event migration when guest upgrades to full account
- Device UUID tracking for guest session management

### Offline Support
- ETag headers for efficient cache validation (304 Not Modified)
- Cache-Control headers optimized for different endpoint types
- `ShallowEtagHeaderFilter` for automatic response hashing

### Data Validation
- Input validation with Jakarta Validation API
- Field-level error messages
- Global exception handling with structured error responses

### Scalability
- Transactional consistency with `@Transactional` annotations
- Connection pooling with HikariCP
- Pagination and filtering support
- Lazy loading of related entities

## Project Structure

```
src/
├── main/java/event_planer/project/
│   ├── controller/          # REST endpoints
│   ├── service/             # Business logic
│   ├── repository/          # Data access
│   ├── entity/              # JPA entities
│   ├── dto/                 # Data transfer objects
│   ├── security/            # JWT & auth logic
│   ├── exception/           # Exception handlers
│   ├── config/              # Spring configuration
│   └── ProjectApplication.java  # Main entry point
├── resources/
│   └── application.properties   # Configuration
└── test/java/              # Unit & integration tests
```

## Configuration

### Environment Variables
Set the following environment variables for local development or production:

```properties
# Full JDBC URL of your MySQL 8+ database
DATABASE_URL=jdbc:mysql://localhost:3306/event_manager_db?serverTimezone=UTC

# Fallback credentials (used only when DATABASE_URL omits user/password)
DB_USERNAME=event_user
DB_PASSWORD=yourpassword

# JWT signing secret — at least 256 bits (32 bytes base64)
JWT_SECRET=your-super-secret-jwt-key
```

For local development, create `src/main/resources/application-local.properties` (not committed to git) and override only what you need.

## API Examples

### Register a User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "SecurePassword123",
    "role": "PRIVATE"
  }'
```

### Create Guest Account
```bash
curl -X POST http://localhost:8080/api/auth/guest
# Returns: { "id": 123, "token": "jwt...", "deviceUuid": "...", "expiresAt": "..." }
```

### Create Event
```bash
curl -X POST http://localhost:8080/api/events \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Team Lunch",
    "description": "Casual team meetup",
    "eventDate": "2026-05-15T12:00:00",
    "locationName": "Central Park",
    "visibility": "PUBLIC"
  }'
```

### Join Event with Participant Name
```bash
curl -X POST http://localhost:8080/api/events/1/join \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{ "participantName": "Alice Smith" }'
```

## Testing

The project includes comprehensive unit tests and integration tests:

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "EventControllerTest"

# Run with coverage report
./gradlew test jacocoTestReport
```

## Deployment

### Deploy to Render (recommended)

The repo ships with a `Dockerfile` and a `render.yaml` that make Render deployment
straightforward.  Follow these steps exactly:

#### 1 · Provision a MySQL database

Render only offers managed **PostgreSQL**.  For MySQL you need an external provider.
Pick any of the free tiers below:

| Provider | Free tier | URL format you'll get |
|---|---|---|
| [PlanetScale](https://planetscale.com) | ✅ | `mysql://user:pass@host/db?sslaccept=strict` |
| [Railway](https://railway.app) | ✅ | `mysql://user:pass@host:port/db` |
| [Aiven](https://aiven.io) | ✅ | `mysql://user:pass@host:port/db?ssl-mode=REQUIRE` |

After creating the database, copy the **connection string** and convert it to JDBC format:

```
# raw URL from provider:
mysql://alice:secret@aws.connect.psdb.cloud/event_db?sslaccept=strict

# JDBC URL (prefix + query params for Spring Boot):
jdbc:mysql://aws.connect.psdb.cloud:3306/event_db?sslMode=REQUIRED&serverTimezone=UTC&user=alice&password=secret
```

> Tip: Keep `sslMode=REQUIRED` — Render requires TLS for outbound DB connections.

#### 2 · Create the Render Web Service

**Option A — Blueprint (recommended, uses `render.yaml` automatically)**

1. Go to [render.com/dashboard](https://dashboard.render.com) → **New** → **Blueprint**.
2. Connect your GitHub repo (`Olly1206/event-planer-backend`).
3. Render reads `render.yaml` and pre-fills all settings.
4. In the *"Set secret env vars"* screen, paste your **`DATABASE_URL`** (JDBC format from Step 1).
5. Click **Apply**.

**Option B — Manual Web Service**

1. Go to [render.com/dashboard](https://dashboard.render.com) → **New** → **Web Service**.
2. Connect your GitHub repo.
3. Set the following fields:

   | Field | Value |
   |---|---|
   | **Runtime** | Docker |
   | **Dockerfile path** | `./Dockerfile` |
   | **Instance type** | Free (or higher) |
   | **Port** | `8080` |

4. Under **Environment Variables**, add:

   | Key | Value |
   |---|---|
   | `DATABASE_URL` | Your JDBC URL from Step 1 |
   | `JWT_SECRET` | Click *"Generate"* to let Render create a secure value |

5. Click **Create Web Service**.

#### 3 · First deploy

Render will:
1. Clone your repo.
2. Build the Docker image (multi-stage — takes ~3–5 min on first run).
3. Start the container on port `8080`.
4. Hibernate auto-creates/updates tables on startup (`ddl-auto=update`).

You can watch the build log live in the Render dashboard.

#### 4 · Verify the deployment

Once the status shows **Live**, open your service URL (e.g. `https://event-planer-backend.onrender.com`):

```bash
# Health-check — should return HTTP 200
curl https://event-planer-backend.onrender.com/

# Swagger UI (if enabled)
open https://event-planer-backend.onrender.com/swagger-ui.html
```

#### 5 · Subsequent deploys

Every push to your default branch (`master`) triggers an automatic redeploy.
No manual action needed.

---

### Run with Docker locally

```bash
# Build
docker build -t event-planer-backend .

# Run (inject env vars inline)
docker run -p 8080:8080 \
  -e DATABASE_URL="jdbc:mysql://localhost:3306/event_manager_db?serverTimezone=UTC" \
  -e DB_USERNAME=event_user \
  -e DB_PASSWORD=yourpassword \
  -e JWT_SECRET=your-secret \
  event-planer-backend
```

### Production JAR (without Docker)

```bash
# Build optimized JAR
./gradlew bootJar -x test

# Run JAR
java -jar build/libs/project-0.0.1-SNAPSHOT.jar
```

## Frontend Integration

This backend is designed to work with the Event Planner Frontend. The frontend should:
- Store JWT tokens from `/api/auth/register` and `/api/auth/login`
- Send tokens in `Authorization: Bearer <token>` header
- Handle 304 Not Modified responses for offline caching
- Cache events using ETag headers for offline access
- Submit optional `participantName` when joining events

## Development Workflow

1. **Feature Branch**: Create branch from `master` (e.g., `feature/guest-mode`)
2. **Changes**: Make code changes and write tests
3. **Test**: Run `./gradlew test` to verify
4. **Commit**: Push to branch with descriptive messages
5. **PR**: Create pull request with testing notes
6. **Review & Merge**: Code review before merging to master

## Known Issues & TODOs

- [ ] Add email verification for registration
- [ ] Implement password reset flow
- [ ] Add event search/filtering UI
- [ ] Add pagination to list endpoints
- [ ] Implement event capacity management
- [ ] Add admin dashboard endpoints
- [ ] Performance optimization for large events

## Contributing

See CONTRIBUTING.md for guidelines.

## License

MIT License - See LICENSE file for details

## Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Contact: dev@eventplanner.local
