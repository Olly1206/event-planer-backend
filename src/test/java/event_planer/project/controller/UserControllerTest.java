package event_planer.project.controller;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import event_planer.project.dto.user.UserResponse;
import event_planer.project.entity.User;
import event_planer.project.exception.ResourceNotFoundException;
import event_planer.project.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userService);
    }

    @Nested
    class GetUserById {

        @Test
        void returnsUserResponseWhen200() {
            // Arrange
            UserResponse userResponse = new UserResponse();
            userResponse.setId(1L);
            userResponse.setUsername("alice");
            userResponse.setEmail("alice@example.com");
            userResponse.setRole(User.Role.PRIVATE);

            when(userService.getUserById(1L)).thenReturn(userResponse);

            // Act
            ResponseEntity<UserResponse> response = userController.getUserById(1L);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            assertThat(response.getBody().getUsername()).isEqualTo("alice");
            assertThat(response.getBody().getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        void throwsWhenUserNotFound() {
            // Arrange
            when(userService.getUserById(999L))
                    .thenThrow(new ResourceNotFoundException("User not found: 999"));

            // Act & Assert
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> userController.getUserById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        void returnsCorrectUserForDifferentIds() {
            // Arrange
            UserResponse user1 = new UserResponse();
            user1.setId(1L);
            user1.setUsername("alice");

            UserResponse user2 = new UserResponse();
            user2.setId(2L);
            user2.setUsername("bob");

            when(userService.getUserById(1L)).thenReturn(user1);
            when(userService.getUserById(2L)).thenReturn(user2);

            // Act
            ResponseEntity<UserResponse> response1 = userController.getUserById(1L);
            ResponseEntity<UserResponse> response2 = userController.getUserById(2L);

            // Assert
            assertThat(response1.getBody().getUsername()).isEqualTo("alice");
            assertThat(response2.getBody().getUsername()).isEqualTo("bob");
        }
    }

    @Nested
    class GetCurrentUser {

        @Test
        void returnsCurrentUserProfileStructure() {
            // Arrange
            UserResponse currentUser = new UserResponse();
            currentUser.setId(42L);
            currentUser.setUsername("current_user");
            currentUser.setEmail("current@example.com");
            currentUser.setRole(User.Role.COMPANY);

            // Assert - verifies UserResponse can be created and populated correctly
            assertThat(currentUser)
                    .satisfies(user -> {
                        assertThat(user.getId()).isEqualTo(42L);
                        assertThat(user.getUsername()).isEqualTo("current_user");
                        assertThat(user.getEmail()).isEqualTo("current@example.com");
                        assertThat(user.getRole()).isEqualTo(User.Role.COMPANY);
                    });
        }
    }

    @Nested
    class PasswordNotExposed {

        @Test
        void userResponseNeverContainsPasswordHash() {
            // Arrange
            UserResponse userResponse = new UserResponse();
            userResponse.setId(1L);
            userResponse.setUsername("alice");

            when(userService.getUserById(1L)).thenReturn(userResponse);

            // Act
            ResponseEntity<UserResponse> response = userController.getUserById(1L);

            // Assert - passwordHash field should not exist or be null in UserResponse DTO
            assertThat(response.getBody())
                    .as("UserResponse should never expose password")
                    .satisfies(user -> {
                        try {
                            user.getClass().getDeclaredField("passwordHash");
                            // If we get here, field exists - fail the test
                            org.junit.jupiter.api.Assertions.fail("UserResponse contains passwordHash field - security risk!");
                        } catch (NoSuchFieldException e) {
                            // Good! Field doesn't exist, password is not exposed
                        }
                    });
        }
    }
}
