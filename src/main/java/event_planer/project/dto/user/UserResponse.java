package event_planer.project.dto.user;

import event_planer.project.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Safe representation of a user for API responses.
 * passwordHash is deliberately excluded — it must never leave the server.
 */
@Data
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private User.Role role;
    private LocalDateTime createdAt;
}
