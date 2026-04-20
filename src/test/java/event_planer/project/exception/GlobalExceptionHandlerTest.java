package event_planer.project.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    class HandleNotFound {

        @Test
        void returns404WithMessage() {
            var ex = new ResourceNotFoundException("Event not found: 42");
            ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("status", 404);
            assertThat(response.getBody()).containsEntry("error", "Event not found: 42");
            assertThat(response.getBody()).containsKey("timestamp");
        }
    }

    @Nested
    class HandleBadRequest {

        @Test
        void returns400WithMessage() {
            var ex = new IllegalArgumentException("Email already in use");
            ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("status", 400);
            assertThat(response.getBody()).containsEntry("error", "Email already in use");
        }
    }

    @Nested
    class HandleConflict {

        @Test
        void returns409WithMessage() {
            var ex = new IllegalStateException("Event is already full");
            ResponseEntity<Map<String, Object>> response = handler.handleConflict(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).containsEntry("status", 409);
            assertThat(response.getBody()).containsEntry("error", "Event is already full");
        }
    }

    @Nested
    class HandleForbidden {

        @Test
        void returns403WithMessage() {
            var ex = new SecurityException("You do not own this event");
            ResponseEntity<Map<String, Object>> response = handler.handleForbidden(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).containsEntry("status", 403);
            assertThat(response.getBody()).containsEntry("error", "You do not own this event");
        }
    }

    @Nested
    class HandleValidation {

        @Test
        @SuppressWarnings("unchecked")
        void returns422WithFieldErrors() {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "email",
                    "Must be a valid email address"));
            bindingResult.addError(new FieldError("request", "password",
                    "Must be at least 8 characters"));

            var ex = new MethodArgumentNotValidException(null, bindingResult);
            ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(response.getBody()).containsEntry("status", 422);
            assertThat(response.getBody()).containsEntry("error", "Validation failed");

            Map<String, String> fieldErrors =
                    (Map<String, String>) response.getBody().get("fieldErrors");
            assertThat(fieldErrors)
                    .containsEntry("email", "Must be a valid email address")
                    .containsEntry("password", "Must be at least 8 characters");
        }
    }

    @Nested
    class HandleAccessDenied {

        @Test
        void returns401() {
            var ex = new AccessDeniedException("Access denied");
            ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).containsEntry("status", 401);
            assertThat(response.getBody()).containsEntry("error", "Authentication required");
        }
    }

    @Nested
    class HandleGeneral {

        @Test
        void returns500ForUnexpectedException() {
            var ex = new RuntimeException("Something broke");
            ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).containsEntry("status", 500);
            assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
        }

        @Test
        void doesNotLeakInternalErrorMessage() {
            var ex = new NullPointerException("sensitive stack trace info");
            ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

            assertThat(response.getBody().get("error").toString())
                    .doesNotContain("sensitive");
        }
    }

    @Nested
    class ResponseStructure {

        @Test
        void allResponsesContainTimestamp() {
            assertThat(handler.handleNotFound(new ResourceNotFoundException("x"))
                    .getBody()).containsKey("timestamp");
            assertThat(handler.handleBadRequest(new IllegalArgumentException("x"))
                    .getBody()).containsKey("timestamp");
            assertThat(handler.handleConflict(new IllegalStateException("x"))
                    .getBody()).containsKey("timestamp");
            assertThat(handler.handleForbidden(new SecurityException("x"))
                    .getBody()).containsKey("timestamp");
            assertThat(handler.handleGeneral(new Exception("x"))
                    .getBody()).containsKey("timestamp");
        }
    }
}
