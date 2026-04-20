package event_planer.project.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs exactly once per HTTP request (OncePerRequestFilter guarantees this).
 *
 * What it does:
 *   1. Reads the "Authorization: Bearer <token>" header
 *   2. Validates the token cryptographically using JwtService
 *   3. If valid, extracts userId + role and stores them in the SecurityContext
 *      so Spring Security knows who this request belongs to
 *
 * If the header is missing/invalid, the filter does nothing and lets Spring
 * Security's access rules decide whether to reject the request (401/403) or
 * allow it (for public endpoints like /api/auth/**).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No bearer token — pass through (public endpoints handle this gracefully)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip the "Bearer " prefix to get the raw token string
        String token = authHeader.substring(7);

        // Only authenticate if token is valid AND the context isn't already set
        if (jwtService.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtService.extractUserId(token);
            String role  = jwtService.extractRole(token);

            // UsernamePasswordAuthenticationToken is Spring Security's standard "who is logged in" object.
            // principal = the userId (Long) — controllers retrieve this via SecurityUtils
            // credentials = null (we don't need the password at this point)
            // authorities = ["ROLE_PRIVATE"] / ["ROLE_COMPANY"] / ["ROLE_ADMIN"]
            //               Spring uses these for @PreAuthorize("hasRole('ADMIN')") etc.
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );

            // Store in SecurityContextHolder — this makes the user "logged in" for this request
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // Always continue the filter chain regardless of auth outcome
        filterChain.doFilter(request, response);
    }
}
