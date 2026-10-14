package com.cypher.gateway.api;

import com.cypher.gateway.repository.IncidentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final IncidentRepository incidents;

    public DashboardController(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of(
                "open", incidents.countByStatus("OPEN"),
                "acknowledged", incidents.countByStatus("ACKNOWLEDGED"),
                "resolved", incidents.countByStatus("RESOLVED"),
                "critical", incidents.countByPriority("CRITICAL"),
                "high", incidents.countByPriority("HIGH")
        );
    }
}