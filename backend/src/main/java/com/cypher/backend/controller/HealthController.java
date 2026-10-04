package com.cypher.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness and readiness.
 *
 * <p>This deliberately does NOT map {@code /}. That route belongs to the
 * operator cockpit, which is served as a static resource from
 * {@code src/main/resources/static/index.html}. An earlier version claimed
 * {@code /} and returned a plain string, which shadowed the cockpit entirely --
 * the browser got "Welcome to CYPHER Backend!" instead of the UI.
 *
 * <p>Uses a non-API prefix so it cannot collide with {@code @RequestMapping("/api")}.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "cypher-backend");
    }
}
