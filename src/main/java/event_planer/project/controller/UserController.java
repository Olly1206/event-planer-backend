package event_planer.project.controller;

import event_planer.project.dto.user.UserResponse;
import event_planer.project.security.SecurityUtils;
import event_planer.project.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    /**
     * GET /api/users/{id}
     * Returns a safe public view of a user.
     * passwordHash is never present in UserResponse so it cannot be returned here.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * GET /api/users/me
     * Header: X-User-Id: <userId>
     *
     * Convenience endpoint — the Android app calls this after login to fetch the
     * current user's profile using the ID extracted from the JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getUserById(SecurityUtils.getCurrentUserId()));
    }
}
