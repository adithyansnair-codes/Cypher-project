package com.cypher.backend.service;

import com.cypher.backend.dto.request.IncidentIngestRequest;
import com.cypher.backend.dto.response.DashboardStatsResponse;
import com.cypher.backend.dto.response.IncidentResponse;
import com.cypher.backend.entity.Camera;
import com.cypher.backend.entity.Incident;
import com.cypher.backend.repository.AlertRepository;
import com.cypher.backend.repository.CameraRepository;
import com.cypher.backend.repository.IncidentRepository;
import com.cypher.backend.websocket.IncidentSocketHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
    private final IncidentSocketHandler socketHandler;

    @PersistenceContext
    private EntityManager entityManager;

    public IncidentService(IncidentRepository incidentRepository,
                           CameraRepository cameraRepository,
                           AlertRepository alertRepository,
                           IncidentSocketHandler socketHandler) {
        this.incidentRepository = incidentRepository;
        this.cameraRepository = cameraRepository;
        this.alertRepository = alertRepository;
        this.socketHandler = socketHandler;
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
        IncidentResponse response = enrich(List.of(incidentRepository.save(incident))).get(0);
        publishAfterCommit("incident.updated", response);
        operatorStep("Operator acknowledged incident #" + incidentId, response.cameraId());
        return response;
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
        IncidentResponse response = enrich(List.of(incidentRepository.save(incident))).get(0);
        publishAfterCommit("incident.updated", response);
        operatorStep("Operator resolved incident #" + incidentId + " -> alerts closed by trigger",
                response.cameraId());
        return response;
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

        SeverityDecision decision = (request.getSeverity() == null || request.getSeverity().isBlank())
                ? deriveSeverity(request.getDetectedObject(), request.getConfidence())
                : new SeverityDecision(request.getSeverity().trim().toUpperCase(),
                        "severity supplied by the caller");
        String severity = decision.severity();

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

        IncidentResponse response = enrich(List.of(saved)).get(0);
        publishAfterCommit("incident.created", response);

        // The reasoning behind this incident, for the cockpit's live trace: the
        // detector's own steps, the rule that graded it, and where it landed.
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("incidentId", response.incidentId());
        detection.put("cameraId", response.cameraId());
        detection.put("cameraCode", response.cameraCode());
        detection.put("title", response.title());
        detection.put("severity", response.severity());
        detection.put("rule", decision.reason());
        detection.put("detectedObject", request.getDetectedObject());
        detection.put("confidence", request.getConfidence());
        detection.put("model", request.getModel());
        detection.put("threshold", request.getThreshold());
        detection.put("inferenceMs", request.getInferenceMs());
        detection.put("source", request.getSource() == null ? "gateway ingest" : request.getSource());
        detection.put("steps", request.getTrace() == null ? List.of() : request.getTrace());
        detection.put("at", Instant.now().toString());
        publishAfterCommit("detection.trace", detection);

        return response;
    }

    /**
     * Priority rules from the project specification. High-risk objects are
     * CRITICAL regardless of confidence; anything else is graded by confidence.
     * Kept in the gateway so a new detector cannot invent its own vocabulary.
     */
    /** A severity and the rule that produced it — the "why" the live trace shows. */
    public record SeverityDecision(String severity, String reason) {
    }

    static SeverityDecision deriveSeverity(String detectedObject, Double confidence) {
        String object = detectedObject == null ? "" : detectedObject.trim().toLowerCase();
        double c = confidence == null ? 0d : confidence;

        // Lethal or life-threatening objects are CRITICAL whatever the confidence.
        // "pistol" and "rifle" were missing here, so a pistol detected at 38%
        // confidence was graded LOW -- the opposite of the intended behaviour.
        // The trained detector's classes are exactly knife and pistol, so this
        // omission would have mis-graded most real detections.
        if (CRITICAL_OBJECTS.contains(object)) {
            return new SeverityDecision(SEVERITY_CRITICAL,
                    "'" + object + "' is a high-risk class -> CRITICAL regardless of confidence");
        }
        if (object.equals("smoke") || object.equals("fall")) {
            return c >= 85
                    ? new SeverityDecision(SEVERITY_HIGH, "'" + object + "' at " + c + "% >= 85% -> HIGH")
                    : new SeverityDecision("MEDIUM", "'" + object + "' at " + c + "% < 85% -> MEDIUM");
        }
        if (c >= 90) return new SeverityDecision(SEVERITY_HIGH, "confidence " + c + "% >= 90% -> HIGH");
        if (c >= 70) return new SeverityDecision("MEDIUM", "confidence " + c + "% >= 70% -> MEDIUM");
        return new SeverityDecision("LOW", "confidence " + c + "% < 70% -> LOW");
    }

    // ------------------------------------------------------------------------

    /**
     * Pushes an event to every connected cockpit, but only once the
     * transaction has actually committed: broadcasting from inside it would
     * announce a change that could still roll back (MoSCoW M-03).
     */
    private void publishAfterCommit(String type, IncidentResponse incident) {
        publishAfterCommit(type, Map.of("incident", incident));
    }

    private void publishAfterCommit(String type, Map<String, Object> body) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    socketHandler.broadcastEvent(type, body);
                }
            });
        } else {
            socketHandler.broadcastEvent(type, body);
        }
    }

    /** A one-line operator action, so the live trace shows the human step too. */
    private void operatorStep(String text, Long cameraId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("cameraId", cameraId);
        event.put("steps", List.of(text));
        event.put("level", "ACTION");
        event.put("at", Instant.now().toString());
        publishAfterCommit("trace", event);
    }

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
