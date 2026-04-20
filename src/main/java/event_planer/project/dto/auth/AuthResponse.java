package event_planer.project.dto.auth;

import event_planer.project.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Sent back to the Android client after a successful login or registration.
 * The client stores the token and sends it in the Authorization header
 * on every subsequent request:  Authorization: Bearer <token>
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Long userId;
    private String username;
    private String email;
    private User.Role role;
}
