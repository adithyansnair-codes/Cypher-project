package com.cypher.backend.dto.response;

import java.util.List;
import java.util.Map;

/**
 * The numbers behind the Analytics view.
 *
 * <p>All of it is aggregated in the database; the cockpit does no counting.
 */
public record AnalyticsResponse(
        Map<String, Long> incidentsBySeverity,
        Map<String, Long> incidentsByStatus,
        List<DayCount> incidentsLast7Days,
        Map<String, Long> camerasByStatus,
        long detectionsTotal,
        long detectionsLast7Days,
        long incidentsTotal
) {

    public record DayCount(String day, long count) {
    }
}
