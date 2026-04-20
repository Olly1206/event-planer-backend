package event_planer.project.dto.auth;

import event_planer.project.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 100)
    private String email;

    // The raw password the user types — service layer will bcrypt-hash this,
    // it is NEVER stored as plaintext
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull(message = "Role is required")
    private User.Role role;

    /**
     * Optional device UUID from guest mode.
     * If provided, the backend will merge the guest's events to this new registered account.
     */
    private String deviceUuid;
}
