package com.cypher.backend.controller;

import com.cypher.backend.dto.request.TraceRequest;
import com.cypher.backend.websocket.IncidentSocketHandler;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live reasoning from the detection pipeline.
 *
 * <p>The AI engine posts what it is doing — frame scanned, boxes collapsed,
 * streak 3/8, cooldown blocking, incident raised — and the gateway relays it to
 * every open cockpit over the same socket as the incident events. Nothing is
 * stored: this is a window onto the algorithm, not a record of it.
 */
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final IncidentSocketHandler socketHandler;

    public TraceController(IncidentSocketHandler socketHandler) {
        this.socketHandler = socketHandler;
    }

    @PostMapping
    public ResponseEntity<?> publish(@Valid @RequestBody TraceRequest request) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("cameraId", request.cameraId());
        event.put("steps", request.steps());
        event.put("level", request.level() == null ? "INFO" : request.level().toUpperCase());
        event.put("model", request.model());
        event.put("threshold", request.threshold());
        event.put("inferenceMs", request.inferenceMs());
        event.put("at", Instant.now().toString());

        socketHandler.broadcastEvent("trace", event);
        return ResponseEntity.accepted().body(Map.of("deliveredTo", socketHandler.liveSessions()));
    }
}
