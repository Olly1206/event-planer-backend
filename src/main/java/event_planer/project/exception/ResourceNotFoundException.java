package event_planer.project.exception;

/**
 * Thrown when a requested database record does not exist.
 * The controller layer maps this to a 404 Not Found HTTP response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
