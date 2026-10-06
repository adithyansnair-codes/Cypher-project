package com.cypher.backend.service;

import com.cypher.backend.dto.request.IncidentIngestRequest;
import com.cypher.backend.dto.response.DashboardStatsResponse;
import com.cypher.backend.dto.response.IncidentResponse;
import com.cypher.backend.entity.Camera;
import com.cypher.backend.entity.Incident;
import com.cypher.backend.repository.AlertRepository;
import com.cypher.backend.repository.CameraRepository;
import com.cypher.backend.repository.IncidentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Incident read model and the operator acknowledge/resolve workflow.
 *
 * <p>Incidents are not created here. In production the AI engine posts them to
 * the gateway, which calls the {@code create_incident_with_alert} stored
 * procedure so the alert row is created in the same transaction.
 */
@Service
public class IncidentService {

    // Must match the chk_incidents_status CHECK constraint added in
    // V3__fix_alert_trigger_and_status_constraints.sql. A value outside this set
    // is rejected by the database rather than silently stored.
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";

    // Must match chk_incidents_severity.
    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH = "HIGH";

    /**
     * Objects that are CRITICAL regardless of how confident the detector was.
     * Keep this in step with the labels the AI engine can emit.
     */
    public static final java.util.Set<String> CRITICAL_OBJECTS = java.util.Set.of(
            "weapon", "gun", "pistol", "rifle", "knife", "fire");

    private final IncidentRepository incidentRepository;
    private final CameraRepository cameraRepository;
    private final AlertRepository alertRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public IncidentService(IncidentRepository incidentRepository,
                           CameraRepository cameraRepository,
                           AlertRepository alertRepository) {
        this.incidentRepository = incidentRepository;
        this.cameraRepository = cameraRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list(String status, String severity) {
        return enrich(incidentRepository.search(
                blankToNull(status), blankToNull(severity)));
    }

    @Transactional(readOnly = true)
    public Optional<IncidentResponse> get(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .map(i -> enrich(List.of(i)).get(0));
    }

    /**
     * Operator acknowledges an incident: OPEN -> ACKNOWLEDGED.
     *
     * @throws IllegalStateException if the incident is not OPEN
     */
    @Transactional
    public IncidentResponse acknowledge(Long incidentId, Long operatorId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("No incident with id " + incidentId));

        if (!STATUS_OPEN.equals(incident.getStatus())) {
            throw new IllegalStateException(
                    "Incident " + incidentId + " is " + incident.getStatus()
                            + ", only OPEN incidents can be acknowledged");
        }

        incident.setStatus(STATUS_ACKNOWLEDGED);
        if (operatorId != null) {
            incident.setAssignedTo(operatorId);
        }
        return enrich(List.of(incidentRepository.save(incident))).get(0);
    }

    /**
     * Operator resolves an incident: OPEN or ACKNOWLEDGED -> RESOLVED.
     *
     * <p>Setting {@code resolvedAt} also fires the {@code trg_close_alerts}
     * database trigger, which closes the incident's alerts.
     *
     * @throws IllegalStateException if already resolved
     */
    @Transactional
    public IncidentResponse resolve(Long incidentId, Long operatorId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("No incident with id " + incidentId));

        if (STATUS_RESOLVED.equals(incident.getStatus())) {
            throw new IllegalStateException("Incident " + incidentId + " is already resolved");
        }

        incident.setStatus(STATUS_RESOLVED);
        incident.setResolvedAt(Instant.now());
        if (operatorId != null) {
            incident.setAssignedTo(operatorId);
        }
        return enrich(List.of(incidentRepository.save(incident))).get(0);
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse stats() {
        // Midnight UTC today. The count is done in the database.
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        return new DashboardStatsResponse(
                incidentRepository.countByStatus(STATUS_OPEN),
                incidentRepository.countBySeverity(SEVERITY_CRITICAL),
                incidentRepository.countBySeverity(SEVERITY_HIGH),
                incidentRepository.countResolvedSince(STATUS_RESOLVED, startOfDay),
                cameraRepository.countByCameraStatus("ONLINE"),
                cameraRepository.count(),
                alertRepository.countByAlertStatus("PENDING")
        );
    }


    // ------------------------------------------------------------------------
    // Ingest: the AI engine's entry point (MoSCoW M-05).
    // ------------------------------------------------------------------------

    /**
     * Records an incident reported by the detection pipeline.
     *
     * <p>Delegates to the {@code create_incident_with_alert} stored procedure so
     * the incident and its alert are written in one transaction, then fires the
     * same priority rules the database function uses.
     *
     * @param request  the detection payload
     * @param reportedBy user id to assign, or null for unassigned
     */
    @Transactional
    public IncidentResponse ingest(IncidentIngestRequest request, Long reportedBy) {
        Camera camera = cameraRepository.findById(request.getCameraId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No camera with id " + request.getCameraId()));

        String severity = (request.getSeverity() == null || request.getSeverity().isBlank())
                ? deriveSeverity(request.getDetectedObject(), request.getConfidence())
                : request.getSeverity().trim().toUpperCase();

        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity)) {
            throw new IllegalArgumentException(
                    "severity must be one of LOW, MEDIUM, HIGH, CRITICAL (got " + severity + ")");
        }

        entityManager.createNativeQuery(
                "CALL create_incident_with_alert(:projectId, :cameraId, :title, :description, :severity, :assignedTo)")
                .setParameter("projectId", camera.getProjectId())
                .setParameter("cameraId", camera.getCameraId())
                .setParameter("title", request.getTitle())
                .setParameter("description", request.getDescription())
                .setParameter("severity", severity)
                .setParameter("assignedTo", reportedBy)
                .executeUpdate();

        // The procedure does not return the new id, so read back the newest
        // incident for this camera -- created in the same transaction.
        Incident saved = incidentRepository
                .findByCameraIdOrderByOccurredAtDesc(camera.getCameraId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Incident was created but could not be read back"));

        return enrich(List.of(saved)).get(0);
    }

    /**
     * Priority rules from the project specification. High-risk objects are
     * CRITICAL regardless of confidence; anything else is graded by confidence.
     * Kept in the gateway so a new detector cannot invent its own vocabulary.
     */
    static String deriveSeverity(String detectedObject, Double confidence) {
        String object = detectedObject == null ? "" : detectedObject.trim().toLowerCase();
        double c = confidence == null ? 0d : confidence;

        // Lethal or life-threatening objects are CRITICAL whatever the confidence.
        // "pistol" and "rifle" were missing here, so a pistol detected at 38%
        // confidence was graded LOW -- the opposite of the intended behaviour.
        // The trained detector's classes are exactly knife and pistol, so this
        // omission would have mis-graded most real detections.
        if (CRITICAL_OBJECTS.contains(object)) {
            return SEVERITY_CRITICAL;
        }
        if (object.equals("smoke") || object.equals("fall")) {
            return c >= 85 ? SEVERITY_HIGH : "MEDIUM";
        }
        if (c >= 90) return SEVERITY_HIGH;
        if (c >= 70) return "MEDIUM";
        return "LOW";
    }

    // ------------------------------------------------------------------------

    /** Joins camera context onto each incident with one query, not N. */
    private List<IncidentResponse> enrich(List<Incident> incidents) {
        if (incidents.isEmpty()) {
            return List.of();
        }
        List<Long> cameraIds = incidents.stream()
                .map(Incident::getCameraId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Camera> cameras = cameraRepository.findAllById(cameraIds).stream()
                .collect(Collectors.toMap(Camera::getCameraId, Function.identity()));

        return incidents.stream()
                .map(i -> {
                    Camera c = i.getCameraId() == null ? null : cameras.get(i.getCameraId());
                    return c == null
                            ? IncidentResponse.of(i)
                            : IncidentResponse.of(i, c.getCameraName(), c.getCameraCode(), c.getLocation());
                })
                .toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toUpperCase();
    }
}
