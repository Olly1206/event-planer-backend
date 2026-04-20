package event_planer.project.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class NoAuthHeader {

        @Test
        void continuesFilterChainWhenNoHeader() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void continuesFilterChainWhenHeaderIsNotBearer() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    class ValidToken {

        @Test
        void setsSecurityContextWithValidToken() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-jwt-token");
            when(jwtService.isTokenValid("valid-jwt-token")).thenReturn(true);
            when(jwtService.extractUserId("valid-jwt-token")).thenReturn(42L);
            when(jwtService.extractRole("valid-jwt-token")).thenReturn("PRIVATE");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(42L);
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_PRIVATE");
        }

        @Test
        void setsCompanyRole() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer company-token");
            when(jwtService.isTokenValid("company-token")).thenReturn(true);
            when(jwtService.extractUserId("company-token")).thenReturn(10L);
            when(jwtService.extractRole("company-token")).thenReturn("COMPANY");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_COMPANY");
        }

        @Test
        void setsAdminRole() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer admin-token");
            when(jwtService.isTokenValid("admin-token")).thenReturn(true);
            when(jwtService.extractUserId("admin-token")).thenReturn(1L);
            when(jwtService.extractRole("admin-token")).thenReturn("ADMIN");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_ADMIN");
        }
    }

    @Nested
    class InvalidToken {

        @Test
        void doesNotSetContextWhenTokenInvalid() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
            when(jwtService.isTokenValid("expired-token")).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    class AlreadyAuthenticated {

        @Test
        void doesNotOverrideExistingAuthentication() throws ServletException, IOException {
            // Pre-set authentication
            var existingAuth = new org.springframework.security.authentication
                    .UsernamePasswordAuthenticationToken(99L, null, java.util.List.of());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            when(request.getHeader("Authorization")).thenReturn("Bearer another-token");
            when(jwtService.isTokenValid("another-token")).thenReturn(true);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // The existing auth should not have been replaced
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getPrincipal()).isEqualTo(99L);
            verify(jwtService, never()).extractUserId(anyString());
        }
    }
}
