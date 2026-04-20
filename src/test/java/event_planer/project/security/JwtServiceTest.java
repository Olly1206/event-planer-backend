package event_planer.project.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // A known Base64-encoded 256-bit key for testing
    private static final String TEST_SECRET = "CCZdzIK9ovJ50kuBnQbxmjgBTOEvUTwEuZXL/2OSTJo=";
    private static final long EXPIRATION_MS = 3_600_000; // 1 hour

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        setField(jwtService, "secret", TEST_SECRET);
        setField(jwtService, "expirationMs", EXPIRATION_MS);
    }

    @Nested
    class GenerateToken {

        @Test
        void generatesNonNullToken() {
            String token = jwtService.generateToken(1L, "PRIVATE");
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        void tokenContainsThreeParts() {
            String token = jwtService.generateToken(1L, "PRIVATE");
            // JWTs have header.payload.signature
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        void differentUsersGetDifferentTokens() {
            String token1 = jwtService.generateToken(1L, "PRIVATE");
            String token2 = jwtService.generateToken(2L, "COMPANY");
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    class ExtractClaims {

        @Test
        void extractsCorrectUserId() {
            String token = jwtService.generateToken(42L, "ADMIN");
            assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        }

        @Test
        void extractsCorrectRole() {
            String token = jwtService.generateToken(1L, "COMPANY");
            assertThat(jwtService.extractRole(token)).isEqualTo("COMPANY");
        }

        @Test
        void extractsPrivateRole() {
            String token = jwtService.generateToken(5L, "PRIVATE");
            assertThat(jwtService.extractRole(token)).isEqualTo("PRIVATE");
        }
    }

    @Nested
    class Validation {

        @Test
        void validTokenReturnsTrue() {
            String token = jwtService.generateToken(1L, "PRIVATE");
            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        void garbageTokenReturnsFalse() {
            assertThat(jwtService.isTokenValid("not.a.token")).isFalse();
        }

        @Test
        void emptyStringReturnsFalse() {
            assertThat(jwtService.isTokenValid("")).isFalse();
        }

        @Test
        void nullReturnsFalse() {
            assertThat(jwtService.isTokenValid(null)).isFalse();
        }

        @Test
        void tamperedTokenReturnsFalse() {
            String token = jwtService.generateToken(1L, "PRIVATE");
            // Flip a character in the signature portion
            String tampered = token.substring(0, token.length() - 2) + "xx";
            assertThat(jwtService.isTokenValid(tampered)).isFalse();
        }

        @Test
        void expiredTokenReturnsFalse() throws Exception {
            // Create a JwtService with 0ms expiration so the token is already expired
            JwtService shortLived = new JwtService();
            setField(shortLived, "secret", TEST_SECRET);
            setField(shortLived, "expirationMs", 0L);

            String token = shortLived.generateToken(1L, "PRIVATE");
            // Small sleep to ensure the token is past expiry
            Thread.sleep(10);
            assertThat(shortLived.isTokenValid(token)).isFalse();
        }

        @Test
        void tokenSignedWithDifferentKeyReturnsFalse() throws Exception {
            // Generate a token with a different secret
            JwtService otherService = new JwtService();
            setField(otherService, "secret", "YURpZmZlcmVudFNlY3JldEtleVRoYXRJczMyQnl0ZXMh");
            setField(otherService, "expirationMs", EXPIRATION_MS);

            String foreignToken = otherService.generateToken(1L, "PRIVATE");
            assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void generateThenExtractPreservesAllClaims() {
            Long userId = 123L;
            String role = "ADMIN";

            String token = jwtService.generateToken(userId, role);

            assertThat(jwtService.isTokenValid(token)).isTrue();
            assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
            assertThat(jwtService.extractRole(token)).isEqualTo(role);
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
