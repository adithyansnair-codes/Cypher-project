package com.cypher.backend.websocket;

import com.cypher.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates the WebSocket handshake with the same JWT the REST API uses.
 *
 * <p>The browser WebSocket API cannot set an {@code Authorization} header, so
 * the token arrives as a query parameter ({@code /ws/incidents?token=...}).
 * Signature and expiry are verified here; anything invalid gets no socket.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String token = tokenOf(request);
        if (token == null || token.isBlank()) {
            log.debug("rejected cockpit socket: no token");
            return false;
        }
        try {
            Claims claims = jwtService.parse(token);
            attributes.put("email", claims.getSubject());
            attributes.put("role", claims.get("role", String.class));
            return true;
        } catch (Exception e) {
            log.debug("rejected cockpit socket: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler handler,
                               Exception exception) {
        // Nothing to do: the handshake either already succeeded or was refused.
    }

    private static String tokenOf(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter("token");
        }
        return null;
    }
}
