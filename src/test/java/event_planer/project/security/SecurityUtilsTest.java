package event_planer.project.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth(Object principal) {
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    class GetCurrentUserId {

        @Test
        void returnsUserIdWhenAuthenticated() {
            setAuth(42L);

            assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(42L);
        }

        @Test
        void throwsWhenNotAuthenticated() {
            assertThatThrownBy(SecurityUtils::getCurrentUserId)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Not authenticated");
        }

        @Test
        void throwsWhenContextHasNullAuth() {
            SecurityContextHolder.clearContext();

            assertThatThrownBy(SecurityUtils::getCurrentUserId)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    class GetCurrentUserIdOrNull {

        @Test
        void returnsUserIdWhenAuthenticated() {
            setAuth(42L);

            assertThat(SecurityUtils.getCurrentUserIdOrNull()).isEqualTo(42L);
        }

        @Test
        void returnsNullWhenNoAuth() {
            assertThat(SecurityUtils.getCurrentUserIdOrNull()).isNull();
        }

        @Test
        void returnsNullForAnonymousUser() {
            var auth = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThat(SecurityUtils.getCurrentUserIdOrNull()).isNull();
        }

        @Test
        void returnsNullWhenPrincipalIsWrongType() {
            setAuth("not-a-long");

            assertThat(SecurityUtils.getCurrentUserIdOrNull()).isNull();
        }
    }
}
