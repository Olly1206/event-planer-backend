# Event Planner Backend - Spring Boot API

A modern RESTful API backend for event planning and management, built with Spring Boot 3.x, Spring Security with JWT, and Spring Data JPA with PostgreSQL.

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
- Java 21 (LTS or later)
- Gradle 8.0+
- PostgreSQL 12+

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
- `GET /api/venues` - Search venues by city, filters, optional `countryCode`; results are sorted by distance

### Vendor Endpoints
- `GET /api/vendors` - Search vendors by city and one or more `optionName` values; duplicate vendors are merged and results are sorted by distance

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
Create `application-local.properties` for local development (not committed to git):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/event_planner
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update

app.jwt.secret=your-super-secret-jwt-key-at-least-256-bits
app.jwt.expiration=86400000

app.cache.max-age.events=300
app.cache.max-age.single-event=600
```

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

### Docker
```bash
# Build Docker image
./gradlew bootBuildImage

# Run container
docker run -p 8080:8080 event-planner-backend:latest
```

### Production Build
```bash
# Build optimized JAR
./gradlew bootJar -x test

# Run JAR
java -jar build/libs/event-planner-backend-*.jar
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
