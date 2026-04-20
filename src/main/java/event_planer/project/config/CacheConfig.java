package event_planer.project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Cache configuration for offline support.
 * - ShallowEtagHeaderFilter: adds ETag headers to responses (used for cache validation)
 * - Cache-Control headers: tells clients how long they can cache responses
 *
 * This enables clients (web/mobile) to:
 * 1. Detect changes using ETags (If-None-Match header)
 * 2. Understand cache duration via Cache-Control
 * 3. Implement efficient offline syncing
 */
@Configuration
public class CacheConfig implements WebMvcConfigurer {

    /**
     * Register ShallowEtagHeaderFilter to automatically add ETag headers
     * to all HTTP responses. This allows clients to validate cached data.
     *
     * How it works:
     * 1. First request: Server computes ETag from response body, returns 200 with body + ETag header
     * 2. Subsequent requests: Client sends If-None-Match with the ETag
     * 3. If data unchanged: Server returns 304 Not Modified (no body transmitted)
     * 4. If data changed: Server returns 200 with new body and new ETag
     *
     * This saves bandwidth on unchanged events.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/events/*");
        registration.setName("etagFilter");
        return registration;
    }
}
