package event_planer.project.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import event_planer.project.dto.auth.GuestAuthResponse;
import event_planer.project.service.UserService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userService);
    }

    @Nested
    class GuestMode {

        @Test
        void createGuestReturns201WithToken() {
            // Arrange
            GuestAuthResponse guestResponse = new GuestAuthResponse(
                    1L,
                    "guest_jwt_token",
                    "device-uuid-123",
                    "2026-05-20T00:00:00",
                    "Welcome! Guest mode active for 30 days. Sign up anytime to keep your events."
            );

            when(userService.createGuestUser()).thenReturn(guestResponse);

            // Act
            ResponseEntity<GuestAuthResponse> response = authController.createGuest();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            assertThat(response.getBody().getToken()).isEqualTo("guest_jwt_token");
            assertThat(response.getBody().getDeviceUuid()).isEqualTo("device-uuid-123");
        }

        @Test
        void guestResponseIncludesExpiryTime() {
            // Arrange
            GuestAuthResponse guestResponse = new GuestAuthResponse(
                    5L,
                    "token",
                    "uuid",
                    "2026-05-20T00:00:00",
                    "Welcome!"
            );

            when(userService.createGuestUser()).thenReturn(guestResponse);

            // Act
            ResponseEntity<GuestAuthResponse> response = authController.createGuest();

            // Assert
            assertThat(response.getBody().getExpiresAt()).isEqualTo("2026-05-20T00:00:00");
        }
    }
}
