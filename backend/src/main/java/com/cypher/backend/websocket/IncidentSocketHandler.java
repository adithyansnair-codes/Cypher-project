package com.cypher.backend.websocket;

import com.cypher.backend.dto.response.IncidentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live incident channel for the cockpit (MoSCoW M-03).
 *
 * <p>Every cockpit keeps a socket open on {@code /ws/incidents}. When the
 * gateway creates, acknowledges or resolves an incident, the change is pushed
 * to every connected operator instead of waiting for the next poll.
 *
 * <p>Dead sessions are dropped on close and on send failure, so a browser tab
 * that goes away cannot leak a session forever.
 */
@Component
public class IncidentSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(IncidentSocketHandler.class);

    private final ObjectMapper objectMapper;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public IncidentSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("cockpit socket {} connected -- {} live", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (sessions.remove(session)) {
            log.info("cockpit socket {} closed ({}) -- {} live",
                    session.getId(), status, sessions.size());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("cockpit socket {} error: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /** @return the number of cockpits currently listening. */
    public int liveSessions() {
        return sessions.size();
    }

    /**
     * Pushes one incident event to every connected cockpit.
     *
     * @param type     {@code incident.created} or {@code incident.updated}
     * @param incident the full incident, the same shape {@code GET
     *                 /api/incidents} returns, so a client needs no second call
     */
    public void broadcast(String type, IncidentResponse incident) {
        broadcastEvent(type, Map.of("incident", incident));
    }

    /**
     * Pushes an arbitrary event to every connected cockpit.
     *
     * <p>{@code body} is merged into the envelope, so
     * {@code broadcastEvent("trace", Map.of("steps", ...))} arrives as
     * {@code {"type": "trace", "steps": [...]}}.
     *
     * @return the number of cockpits the event reached
     */
    public int broadcastEvent(String type, Map<String, Object> body) {
        if (sessions.isEmpty()) {
            return 0;
        }

        final String payload;
        try {
            Map<String, Object> envelope = new java.util.LinkedHashMap<>();
            envelope.put("type", type);
            envelope.putAll(body);
            payload = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            // Serialising an event must never take the caller's work down.
            log.error("could not serialise a {} event: {}", type, e.getMessage());
            return 0;
        }

        TextMessage message = new TextMessage(payload);
        int delivered = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                // A WebSocketSession is not safe for concurrent sends from
                // different threads, so writes to one session are serialised.
                synchronized (session) {
                    session.sendMessage(message);
                }
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.warn("dropping cockpit socket {}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
        return delivered;
    }
}
