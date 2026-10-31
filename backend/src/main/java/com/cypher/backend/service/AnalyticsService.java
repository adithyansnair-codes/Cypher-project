package com.cypher.backend.service;

import com.cypher.backend.dto.response.AnalyticsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregates for the Analytics view.
 *
 * <p>Native SQL rather than entity queries: these are counts over the whole
 * table, which is exactly what the database is good at.
 */
@Service
public class AnalyticsService {

    private static final String BY_SEVERITY = """
            SELECT severity, COUNT(*) FROM incidents GROUP BY severity
            """;

    private static final String BY_STATUS = """
            SELECT status, COUNT(*) FROM incidents GROUP BY status
            """;

    private static final String PER_DAY = """
            SELECT CAST(occurred_at AS DATE) AS day, COUNT(*)
              FROM incidents
             WHERE occurred_at >= :since
             GROUP BY day
             ORDER BY day
            """;

    private static final String CAMERAS_BY_STATUS = """
            SELECT camera_status, COUNT(*) FROM cameras GROUP BY camera_status
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        return new AnalyticsResponse(
                counts(BY_SEVERITY),
                counts(BY_STATUS),
                perDay(),
                counts(CAMERAS_BY_STATUS),
                scalar("SELECT COUNT(*) FROM detections"),
                ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM detections WHERE detected_at >= :since")
                        .setParameter("since", Instant.now().minus(7, ChronoUnit.DAYS))
                        .getSingleResult()).longValue(),
                scalar("SELECT COUNT(*) FROM incidents"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> counts(String sql) {
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private List<AnalyticsResponse.DayCount> perDay() {
        List<Object[]> rows = entityManager.createNativeQuery(PER_DAY)
                .setParameter("since", LocalDate.now(ZoneOffset.UTC).minusDays(6)
                        .atStartOfDay(ZoneOffset.UTC).toInstant()).getResultList();
        return rows.stream()
                .map(row -> new AnalyticsResponse.DayCount(
                        String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    private long scalar(String sql) {
        Object value = entityManager.createNativeQuery(sql).getSingleResult();
        return ((Number) value).longValue();
    }
}
