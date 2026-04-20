package event_planer.project.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response after successful guest login.
 * Guest mode allows browsing/creating events without email registration.
 * Guest expires after 30 days and can be linked to a registered account.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestAuthResponse {
    private Long id;
    private String token;         // JWT token with guest role
    private String deviceUuid;    // Device identifier
    private String expiresAt;     // When this guest account expires (ISO-8601)
    private String message;       // e.g., "Welcome! Guest mode active for 30 days. Sign up anytime to keep your events."
}
