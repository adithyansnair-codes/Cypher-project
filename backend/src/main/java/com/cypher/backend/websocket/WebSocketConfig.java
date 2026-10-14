package com.cypher.backend.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * Registers the live incident channel at {@code /ws/incidents} (MoSCoW M-03).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final IncidentSocketHandler incidentSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * Extra origins allowed to open the socket, comma-separated. Empty (the
     * default) keeps Spring's same-origin rule, which covers the normal
     * deployment where the gateway serves the cockpit itself. Behind
     * code-server's port proxy the public origin differs from the gateway's,
     * so such deployments list their public origin(s) here.
     */
    @Value("${cypher.ws.allowed-origins:}")
    private String[] allowedOrigins;

    public WebSocketConfig(IncidentSocketHandler incidentSocketHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.incidentSocketHandler = incidentSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry
                .addHandler(incidentSocketHandler, "/ws/incidents")
                .addInterceptors(jwtHandshakeInterceptor);

        String[] origins = allowedOrigins == null
                ? new String[0]
                : Arrays.stream(allowedOrigins)
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new);

        if (origins.length > 0) {
            // Setting origins explicitly replaces the same-origin default, so
            // the list has to include every way the cockpit is reached.
            registration.setAllowedOriginPatterns(origins);
        }
    }
}
