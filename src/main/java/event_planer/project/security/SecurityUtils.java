package event_planer.project.security;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience helper for controllers to get the currently authenticated user's ID.
 *
 * The JwtAuthFilter stores the userId (Long) as the principal in the SecurityContext
 * after validating the JWT. This utility retrieves it without controllers needing
 * to know anything about how JWT works.
 */
public class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the database ID of the currently authenticated user.
     * Only call this from endpoints that are protected (require authentication) —
     * the SecurityConfig ensures unauthenticated requests never reach those endpoints.
     */
    public static Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return (Long) auth.getPrincipal();
    }

    /**
     * Returns the current user's ID, or {@code null} if the request is unauthenticated.
     * Use this for endpoints that are accessible to both logged-in and anonymous users
     * (e.g. viewing a single event — public users see public data, logged-in users
     * see extra fields like the invite token).
     */
    public static Long getCurrentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return (Long) auth.getPrincipal();
        } catch (ClassCastException e) {
            return null;
        }
    }
}
