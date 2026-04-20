package event_planer.project.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Responsible for everything JWT-related:
 *   - Generating a signed token after login/register
 *   - Validating an incoming token (signature + expiry)
 *   - Extracting the userId and role claims from a token
 */
@Component
public class JwtService {

    // Injected from application.properties — never hardcoded
    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Builds a JWT with:
     *   subject  = the user's database ID (as a String — JWT subjects are always Strings)
     *   claim    = the user's role ("PRIVATE", "COMPANY", or "ADMIN")
     *   expiry   = now + expirationMs (default 1 hour)
     *
     * The token is signed with HMAC-SHA256 using the secret key.
     * Tampering with any part of the token invalidates the signature.
     */
    public String generateToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // Extract the userId from a validated token's subject claim
    public Long extractUserId(String token) {
        return Long.parseLong(extractClaims(token).getSubject());
    }

    // Extract the role string from a validated token
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    /**
     * Returns true only if the token:
     *   1. Has a valid JJWT signature (wasn't tampered with)
     *   2. Has not expired
     * Any JwtException (ExpiredJwtException, MalformedJwtException, etc.) means invalid.
     */
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Parses and verifies the token — throws JwtException if anything is wrong
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Decodes the Base64 secret from properties into a real cryptographic key object
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
