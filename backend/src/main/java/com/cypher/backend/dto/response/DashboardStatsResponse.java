package com.cypher.backend.dto.response;

import java.util.Map;

/**
 * The four counters shown at the top of the cockpit.
 *
 * <p>Matches the tiles in the "Live cockpit" design: Open incidents, Critical
 * priority, High priority, Resolved today.
 */
public record DashboardStatsResponse(
        long openIncidents,
        long criticalPriority,
        long highPriority,
        long resolvedToday,
        long camerasOnline,
        long camerasTotal,
        long pendingAlerts
) {

    public Map<String, Long> asMap() {
        return Map.of(
                "openIncidents", openIncidents,
                "criticalPriority", criticalPriority,
                "highPriority", highPriority,
                "resolvedToday", resolvedToday,
                "camerasOnline", camerasOnline,
                "camerasTotal", camerasTotal,
                "pendingAlerts", pendingAlerts
        );
    }
}
