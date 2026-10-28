package com.cypher.backend.service;

import com.cypher.backend.repository.IncidentRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Application-level incident metrics exposed through Micrometer/Prometheus. */
@Component
public class IncidentMetrics {

    private final MeterRegistry registry;

    public IncidentMetrics(MeterRegistry registry, IncidentRepository incidents) {
        this.registry = registry;
        Gauge.builder("cypher.incidents.open", incidents,
                        repository -> repository.countByStatus(IncidentService.STATUS_OPEN))
                .description("Incidents currently in OPEN status")
                .register(registry);
    }

    public void created(String severity, String detectedObject) {
        registry.counter("cypher.incidents.ingested",
                        "severity", safe(severity),
                        "object", safe(detectedObject))
                .increment();
    }

    public void action(String action) {
        registry.counter("cypher.incidents.actions", "action", action).increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase();
    }
}
