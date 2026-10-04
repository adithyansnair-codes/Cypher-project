package com.cypher.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for browser clients.
 *
 * <p>In the normal setup the cockpit is served from
 * {@code backend/src/main/resources/static}, so it shares an origin with the API
 * and CORS never applies. This exists for the development case: a Vite dev
 * server or a file opened directly, both of which give the page a different
 * origin.
 *
 * <p>The allowed origins are an explicit list, not {@code *}. This API carries a
 * bearer token, so a wildcard would let any page on the internet call it with a
 * user's credentials.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cypher.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .exposedHeaders("Location")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
