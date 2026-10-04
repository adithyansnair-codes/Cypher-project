package com.cypher.backend.dto.response;

import com.cypher.backend.entity.Incident;

import java.time.Instant;

/**
 * An incident enriched with the camera context an operator needs.
 *
 * <p>{@code Incident} only stores {@code cameraId}; the cockpit has to show
 * *where* an incident happened, so the camera name and location are joined in
 * here rather than making the browser issue a second request per row.
 */
public record IncidentResponse(
        Long incidentId,
        String title,
        String description,
        String severity,
        String status,
        Instant occurredAt,
        Instant resolvedAt,
        Long assignedTo,
        Long cameraId,
        String cameraName,
        String cameraCode,
        String location,
        Long detectionId
) {

    /** Builds a response when the camera is known. */
    public static IncidentResponse of(Incident i, String cameraName, String cameraCode, String location) {
        return new IncidentResponse(
                i.getIncidentId(),
                i.getTitle(),
                i.getDescription(),
                i.getSeverity(),
                i.getStatus(),
                i.getOccurredAt(),
                i.getResolvedAt(),
                i.getAssignedTo(),
                i.getCameraId(),
                cameraName,
                cameraCode,
                location,
                i.getDetectionId()
        );
    }

    /** Builds a response when the camera row could not be found. */
    public static IncidentResponse of(Incident i) {
        return of(i, null, null, null);
    }
}
