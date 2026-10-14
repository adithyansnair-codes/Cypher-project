package com.cypher.backend.service;

import com.cypher.backend.dto.response.AuditEntryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Reads the audit trail (MoSCoW S-06).
 *
 * <p>Rows are written by the {@code trg_incident_changes} database trigger, so
 * the trail exists whether a change came from the API, the AI engine or psql.
 * There is deliberately no entity for {@code audit_logs}: nothing in the
 * application writes it, so the read side stays a projection.
 */
@Service
public class AuditService {

    private static final String RECENT = """
            SELECT a.audit_id,
                   a.event_type,
                   a.entity_name,
                   a.entity_id,
                   COALESCE(
                       NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), ''),
                       u.email,
                       'system') AS actor,
                   a.new_values ->> 'status'   AS status,
                   a.new_values ->> 'severity' AS severity,
                   a.created_at
              FROM audit_logs a
              LEFT JOIN users u ON u.user_id = a.user_id
             ORDER BY a.audit_id DESC
             LIMIT :limit
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** Newest first, capped so one request cannot pull the whole table. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<AuditEntryResponse> recent(int limit) {
        List<Object[]> rows = entityManager
                .createNativeQuery(RECENT)
                .setParameter("limit", Math.clamp(limit, 1, 500))
                .getResultList();

        return rows.stream()
                .map(row -> new AuditEntryResponse(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        row[3] == null ? null : ((Number) row[3]).longValue(),
                        (String) row[4],
                        (String) row[5],
                        (String) row[6],
                        toInstant(row[7])))
                .toList();
    }

    /**
     * A native query hands back whatever the driver produced for
     * {@code timestamptz}. Hibernate 7 gives an {@link Instant}; an older
     * driver would give a {@link Timestamp}. Accept either.
     */
    private static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case Timestamp timestamp -> timestamp.toInstant();
            case java.util.Date date -> date.toInstant();
            default -> null;
        };
    }
}
