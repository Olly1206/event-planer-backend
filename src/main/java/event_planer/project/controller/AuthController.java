package event_planer.project.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import event_planer.project.dto.auth.AuthResponse;
import event_planer.project.dto.auth.GuestAuthResponse;
import event_planer.project.dto.auth.LoginRequest;
import event_planer.project.dto.auth.RegisterRequest;
import event_planer.project.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, and guest mode endpoints (no auth required)")
public class AuthController {

    private final UserService userService;

    /**
     * POST /api/auth/register
     * Body: { "username": "...", "email": "...", "password": "...", "role": "PRIVATE", "deviceUuid": "optional" }
     *
     * @Valid triggers all the @NotBlank, @Email, @Size constraints on RegisterRequest.
     * If any fail, GlobalExceptionHandler catches the exception and returns 422
     * with field-level error messages — the service method is never called.
     *
     * deviceUuid is optional: if provided, the backend merges all guest events to this new account.
     *
     * Returns 201 Created on success with a JSON body containing the JWT token.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     * Body: { "email": "...", "password": "..." }
     *
     * Returns 200 OK with the JWT token and user info on success.
     * GlobalExceptionHandler returns 400 if credentials are wrong.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/guest
     * Creates a temporary guest account without email/password.
     * Guest expires after 30 days and can be linked to a registered account later.
     *
     * Returns 201 Created with guest JWT token, device UUID, and expiry time.
     */
    @PostMapping("/guest")
    public ResponseEntity<GuestAuthResponse> createGuest() {
        GuestAuthResponse response = userService.createGuestUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
