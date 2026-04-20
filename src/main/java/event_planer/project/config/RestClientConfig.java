package event_planer.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate with connection and read timeouts.
 *
 * Prevents indefinite hangs when external APIs (Nominatim, Overpass, Open-Meteo)
 * are slow or unresponsive. Timeouts are set conservatively:
 * - Connection timeout: 5 seconds (time to establish TCP connection)
 * - Read timeout: 15 seconds (time to receive response after connection)
 *
 * If an external API takes longer than these limits, the request fails gracefully
 * rather than hanging indefinitely.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory());
        return restTemplate;
    }

    /**
     * ClientHttpRequestFactory with explicit socket and read timeouts.
     * Prevents the application from waiting indefinitely on slow external APIs.
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 seconds to establish connection
        factory.setReadTimeout(15000);    // 15 seconds to receive response
        return new BufferingClientHttpRequestFactory(factory);
    }
}
