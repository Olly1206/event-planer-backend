package event_planer.project.service;

import event_planer.project.dto.auth.AuthResponse;
import event_planer.project.dto.auth.LoginRequest;
import event_planer.project.dto.auth.RegisterRequest;
import event_planer.project.dto.user.UserResponse;
import event_planer.project.entity.User;
import event_planer.project.exception.ResourceNotFoundException;
import event_planer.project.repository.UserRepository;
import event_planer.project.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role(User.Role.PRIVATE)
                .build();
    }

    // ── Register ───────────────────────────────────────────────────────────────

    @Nested
    class Register {

        @Test
        void registersSuccessfully() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("bob");
            request.setEmail("bob@example.com");
            request.setPassword("password123");
            request.setRole(User.Role.PRIVATE);

            when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
            when(userRepository.existsByUsername("bob")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(2L);
                return u;
            });
            when(jwtService.generateToken(2L, "PRIVATE")).thenReturn("jwt-token-xyz");

            AuthResponse response = userService.register(request);

            assertThat(response.getToken()).isEqualTo("jwt-token-xyz");
            assertThat(response.getUsername()).isEqualTo("bob");
            assertThat(response.getEmail()).isEqualTo("bob@example.com");
            assertThat(response.getRole()).isEqualTo(User.Role.PRIVATE);
            verify(passwordEncoder).encode("password123");
        }

        @Test
        void throwsWhenEmailAlreadyRegistered() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("bob");
            request.setEmail("alice@example.com");
            request.setPassword("password123");
            request.setRole(User.Role.PRIVATE);

            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email is already registered");
        }

        @Test
        void throwsWhenUsernameAlreadyTaken() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("alice");
            request.setEmail("new@example.com");
            request.setPassword("password123");
            request.setRole(User.Role.PRIVATE);

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username is already taken");
        }

        @Test
        void passwordIsNeverStoredAsPlaintext() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("bob");
            request.setEmail("bob@example.com");
            request.setPassword("mySecret");
            request.setRole(User.Role.COMPANY);

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode("mySecret")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(3L);
                return u;
            });
            when(jwtService.generateToken(anyLong(), anyString())).thenReturn("token");

            userService.register(request);

            verify(userRepository).save(argThat(user ->
                    user.getPasswordHash().equals("$2a$10$encoded")
                            && !user.getPasswordHash().equals("mySecret")
            ));
        }
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        void loginSuccessfully() {
            LoginRequest request = new LoginRequest();
            request.setEmail("alice@example.com");
            request.setPassword("correctPassword");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);
            when(jwtService.generateToken(1L, "PRIVATE")).thenReturn("jwt-token-abc");

            AuthResponse response = userService.login(request);

            assertThat(response.getToken()).isEqualTo("jwt-token-abc");
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("alice");
        }

        @Test
        void throwsWhenEmailNotFound() {
            LoginRequest request = new LoginRequest();
            request.setEmail("unknown@example.com");
            request.setPassword("whatever");

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No account found");
        }

        @Test
        void throwsWhenPasswordIncorrect() {
            LoginRequest request = new LoginRequest();
            request.setEmail("alice@example.com");
            request.setPassword("wrongPassword");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrongPassword", "$2a$10$hashedpassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Incorrect password");
        }
    }

    // ── GetUserById ────────────────────────────────────────────────────────────

    @Nested
    class GetUserById {

        @Test
        void returnsUserResponse() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            UserResponse response = userService.getUserById(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("alice");
            assertThat(response.getEmail()).isEqualTo("alice@example.com");
            assertThat(response.getRole()).isEqualTo(User.Role.PRIVATE);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ── Guest Mode ─────────────────────────────────────────────────────────────

    @Nested
    class GuestMode {

        @Test
        void createGuestUserSuccessfully() {
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(99L);
                return u;
            });
            when(jwtService.generateToken(99L, "GUEST")).thenReturn("guest-jwt-token");

            var response = userService.createGuestUser();

            assertThat(response.getId()).isEqualTo(99L);
            assertThat(response.getToken()).isEqualTo("guest-jwt-token");
            assertThat(response.getDeviceUuid()).isNotNull();
            assertThat(response.getExpiresAt()).isNotNull();
            assertThat(response.getMessage()).contains("30 days");

            verify(userRepository).save(argThat(user ->
                    user.getUsername().startsWith("guest_")
                            && user.getEmail() == null
                            && user.getPasswordHash() == null
                            && user.getRole() == User.Role.GUEST
            ));
        }

        @Test
        void guestTokenIsGeneratedWithGuestRole() {
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(100L);
                return u;
            });
            when(jwtService.generateToken(100L, "GUEST")).thenReturn("guest-token-xyz");

            userService.createGuestUser();

            verify(jwtService).generateToken(100L, "GUEST");
        }
    }
}
