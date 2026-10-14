package com.cypher.backend.dto.response;

import java.time.Instant;

/**
 * One row of the audit trail (MoSCoW S-06).
 *
 * <p>{@code audit_logs} stores the whole row before and after a change as
 * JSONB. The cockpit only needs the headline for each entry, so the status and
 * severity are lifted out of {@code new_values} in the query.
 */
public record AuditEntryResponse(
        Long auditId,
        String eventType,
        String entityName,
        Long entityId,
        String actor,
        String status,
        String severity,
        Instant createdAt
) {
}
