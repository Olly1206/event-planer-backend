package event_planer.project.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Intercepts exceptions thrown anywhere in a controller or service and converts
 * them into structured JSON error responses instead of raw stack traces.
 *
 * Without this, Spring returns a generic 500 page. With it, Android gets a clean
 * JSON body like:  { "status": 404, "error": "Event not found: 7", "timestamp": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — record not found in the database
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 400 — caller passed invalid business data (e.g. duplicate email, already joined)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 409 — conflicting state (e.g. event already full, already a participant)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 403 — authenticated user tried to modify something they don't own
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * 422 — @Valid annotation on a request DTO found constraint violations.
     * Collects ALL field errors into one response so Android can show all
     * validation problems at once instead of one at a time.
     *
     * Example output:
     * { "status": 422, "error": "Validation failed",
     *   "fieldErrors": { "email": "Must be a valid email address", "password": "..." } }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage(),
                        // If two constraints fail on the same field, keep the first message
                        (msg1, msg2) -> msg1
                ));

        Map<String, Object> body = new HashMap<>();
        body.put("status", 422);
        body.put("error", "Validation failed");
        body.put("fieldErrors", fieldErrors);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(422).body(body);
    }

    // 401 — Spring Security's AccessDeniedException when no valid JWT is present
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    // Fallback — catches anything else as a 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
