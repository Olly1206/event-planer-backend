package event_planer.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import event_planer.project.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity   // enables @PreAuthorize annotations on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // Stateless — no HTTP session is created or used; every request must carry its own JWT
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public — no token required
                .requestMatchers("/api/auth/**").permitAll()
                // OpenAPI / Swagger UI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // These event endpoints always require a valid JWT (they call getCurrentUserId())
                .requestMatchers(HttpMethod.GET, "/api/events/joined").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/events/my").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/events/*/invite-link").authenticated()
                // Public invite preview does NOT need a token
                .requestMatchers(HttpMethod.GET, "/api/events/invite/**").permitAll()
                // Some clients probe links with HEAD before opening them
                .requestMatchers(HttpMethod.HEAD, "/api/events/invite/**").permitAll()
                // Anyone can browse/search events without being logged in
                .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                // Venue suggestions are public (informational — no auth needed)
                .requestMatchers(HttpMethod.GET, "/api/venues/**").permitAll()
                // Vendor suggestions are public (informational — no auth needed)
                .requestMatchers(HttpMethod.GET, "/api/vendors/**").permitAll()
                // Invite landing page (Thymeleaf HTML) — public
                .requestMatchers(HttpMethod.GET, "/invite/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/invite/**").permitAll()
                // Every other endpoint requires a valid JWT
                .anyRequest().authenticated()
            )
            // Insert JWT filter before Spring's own username/password filter so the
            // SecurityContext is populated before authorisation checks run
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
