# Contributing to Event Planner Backend

Thank you for your interest in contributing to the Event Planner Backend! This guide will help you get started.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/event-planner-backend.git
   cd event-planner-backend
   ```
3. **Create a branch** for your feature:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. **Set up your development environment**:
   ```bash
   ./gradlew build
   ```

## Development Guidelines

### Code Style
- Follow Google Java Style Guide
- Use meaningful variable names
- Add comments for complex logic
- Keep methods small and focused
- Use immutable objects where possible (Lombok `@Value`)

### Commit Messages
```
Type: Brief description (50 chars max)

Longer explanation if needed (wrap at 72 chars)
- Bullet points are fine too
- Reference issues if relevant: Fixes #123
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `test`: Adding/updating tests
- `docs`: Documentation changes
- `chore`: Build, dependencies, etc.

### Testing

**Every PR must include tests:**

```java
@Test
void describeWhatYouAreTesting() {
    // Arrange
    User user = new User();
    
    // Act
    UserResponse response = userService.getUser(user.getId());
    
    // Assert
    assertThat(response.getName()).isEqualTo(user.getName());
}
```

- Use nested `@Nested` classes for logical grouping
- Mock external dependencies with `@Mock`
- Use `@ExtendWith(MockitoExtension.class)` for unit tests
- Aim for >80% code coverage

**Run tests:**
```bash
./gradlew test
```

### Adding New Features

1. **Create DTOs** in `dto/` for request/response objects
   ```java
   @Data
   public class MyRequest {
       @NotBlank(message = "Field is required")
       private String field;
   }
   ```

2. **Implement Service Layer** in `service/`
   ```java
   @Service
   @RequiredArgsConstructor
   @Transactional  // For write operations
   public class MyService {
       // Implement business logic
   }
   ```

3. **Create Repository** in `repository/` (if needed)
   ```java
   @Repository
   public interface MyRepository extends JpaRepository<MyEntity, Long> {
       Optional<MyEntity> findByName(String name);
   }
   ```

4. **Expose via Controller** in `controller/`
   ```java
   @PostMapping
   public ResponseEntity<MyResponse> create(@Valid @RequestBody MyRequest request) {
       return ResponseEntity.status(HttpStatus.CREATED)
               .body(myService.create(request));
   }
   ```

5. **Add Tests** for each layer

## Pull Request Process

1. **Update README.md** if adding new features or APIs
2. **Run all tests** locally:
   ```bash
   ./gradlew clean test build
   ```
3. **Push your branch**:
   ```bash
   git push origin feature/your-feature-name
   ```
4. **Create a Pull Request** on GitHub with:
   - Clear title and description
   - Link to related issues
   - Summary of changes
   - Any testing notes

5. **Address review feedback** and update your PR

## Code Review Checklist

Reviewers will check:
- ✅ Tests are included and passing
- ✅ Code follows style guidelines
- ✅ No hardcoded values or secrets
- ✅ Error handling is appropriate
- ✅ Documentation is clear
- ✅ Performance implications considered
- ✅ Security best practices followed

## Security Considerations

- **Never commit secrets** (use environment variables)
- **Always validate user input** (use Jakarta Validation)
- **Hash passwords** with bcrypt (use Spring Security)
- **Implement authentication checks** with `@PreAuthorize`
- **Use HTTPS in production**
- **Keep dependencies updated** for security patches

## Documentation

- **Update API comments** with JavaDoc for public methods
- **Document breaking changes** in CHANGELOG
- **Add examples** in README if adding new endpoints
- **Comment complex logic** to explain intent

Example JavaDoc:
```java
/**
 * Creates a new event and assigns it to the organiser.
 * 
 * @param request contains event details (title, date, etc)
 * @param organiserId ID of the user creating the event
 * @return EventResponse with generated event ID and full details
 * @throws IllegalArgumentException if event data is invalid
 */
@Transactional
public EventResponse createEvent(CreateEventRequest request, Long organiserId) {
    // Implementation
}
```

## Project Structure Reference

```
src/main/java/event_planer/project/
├── controller/       # REST endpoints
│   └── EventController.java
├── service/          # Business logic
│   └── EventService.java
├── repository/       # Data access
│   └── EventRepository.java
├── entity/           # JPA entities
│   └── Event.java
├── dto/              # Data transfer objects
│   ├── CreateEventRequest.java
│   └── EventResponse.java
├── security/         # JWT & auth
│   ├── JwtService.java
│   └── SecurityUtils.java
├── exception/        # Exception handlers
│   └── GlobalExceptionHandler.java
├── config/           # Spring configuration
│   └── SecurityConfig.java
└── ProjectApplication.java
```

## Common Tasks

### Adding a New Endpoint

1. Create DTO request/response classes
2. Add repository method if needed
3. Implement service method with business logic
4. Add controller method with `@PostMapping`/`@GetMapping`/etc.
5. Add unit tests for service method
6. Add integration tests for controller method
7. Test locally with cURL or Postman
8. Document in README

### Fixing a Bug

1. Write a test that reproduces the bug (should fail)
2. Fix the code to make the test pass
3. Run all tests to ensure no regression
4. Create PR with `fix:` commit message

### Performance Optimization

- Profile first with `@Transactional(readOnly = true)` for queries
- Use pagination for large result sets
- Consider caching with `@Cacheable`
- Avoid N+1 queries with proper JPA joins

## Questions?

- Check existing issues for solutions
- Ask in PRs - our team is happy to help
- Review similar features in the codebase

## Recognition

Contributors will be added to CONTRIBUTORS.md file! 🎉
