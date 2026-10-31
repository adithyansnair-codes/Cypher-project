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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
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
 * The detection, incident and alert are saved in one transaction.
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
    private final IncidentMetrics metrics;
    private final JdbcTemplate jdbc;
    private final DatabaseDialect dialect;

    @PersistenceContext
    private EntityManager entityManager;

    public IncidentService(IncidentRepository incidentRepository,
                           CameraRepository cameraRepository,
                           AlertRepository alertRepository,
                           IncidentSocketHandler socketHandler,
                           IncidentMetrics metrics, JdbcTemplate jdbc, DatabaseDialect dialect) {
        this.incidentRepository = incidentRepository;
        this.cameraRepository = cameraRepository;
        this.alertRepository = alertRepository;
        this.socketHandler = socketHandler;
        this.metrics = metrics;
        this.jdbc = jdbc;
        this.dialect = dialect;
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
        metrics.action("acknowledge");
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

        if (!List.of(STATUS_OPEN, STATUS_ACKNOWLEDGED).contains(incident.getStatus())) {
            throw new IllegalStateException("Incident " + incidentId + " cannot be resolved from " + incident.getStatus());
        }

        incident.setStatus(STATUS_RESOLVED);
        incident.setResolvedAt(Instant.now());
        if (operatorId != null) {
            incident.setAssignedTo(operatorId);
        }
        IncidentResponse response = enrich(List.of(incidentRepository.save(incident))).get(0);
        publishAfterCommit("incident.updated", response);
        metrics.action("resolve");
        operatorStep("Operator resolved incident #" + incidentId + " -> alerts closed by trigger",
                response.cameraId());
        return response;
    }

    /**
     * Permanently remove an incident. Alerts and media reference incidents with
     * ON DELETE CASCADE. Detection history is intentionally retained for
     * analytics, but it can no longer point at an incident.
     */
    @Transactional
    public void delete(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No incident with id " + incidentId));
        Long cameraId = incident.getCameraId();
        incidentRepository.delete(incident);
        metrics.action("delete");
        publishAfterCommit("incident.deleted", Map.of(
                "incidentId", incidentId,
                "cameraId", cameraId == null ? 0L : cameraId
        ));
        operatorStep("Operator deleted incident #" + incidentId, cameraId);
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
     * Saves the incident and alert together, using the inserted incident ID.
     *
     * @param request  the detection payload
     * @param reportedBy user id to assign, or null for unassigned
     */
    @Transactional
    public IncidentResponse ingest(IncidentIngestRequest request, Long reportedBy) {
        Camera camera = cameraRepository.findById(request.getCameraId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No camera with id " + request.getCameraId()));

        SeverityDecision decision = (request.getDetectedObject() != null && !request.getDetectedObject().isBlank())
                || request.getSeverity() == null || request.getSeverity().isBlank()
                ? deriveSeverity(request.getDetectedObject(), request.getConfidence())
                : new SeverityDecision(request.getSeverity().trim().toUpperCase(),
                        "severity supplied by the caller");
        String severity = decision.severity();

        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity)) {
            throw new IllegalArgumentException(
                    "severity must be one of LOW, MEDIUM, HIGH, CRITICAL (got " + severity + ")");
        }

        // Job + detection are written in this same transaction as the incident
        // and alert. If any insert fails, rollback removes all four instead of
        // leaving an incident with no underlying detection for Analytics.
        Long detectionId = recordDetection(request, camera);

        Incident saved = new Incident();
        saved.setProjectId(camera.getProjectId());
        saved.setCameraId(camera.getCameraId());
        saved.setDetectionId(detectionId);
        saved.setTitle(request.getTitle());
        saved.setDescription(request.getDescription());
        saved.setSeverity(severity);
        saved.setStatus(STATUS_OPEN);
        saved.setAssignedTo(reportedBy);
        saved.setOccurredAt(Instant.now());
        saved = incidentRepository.saveAndFlush(saved);
        jdbc.update("""
                INSERT INTO alerts (incident_id, alert_type, alert_message, alert_status)
                VALUES (?, 'INCIDENT', ?, 'PENDING')
                """, saved.getIncidentId(), "New Incident: " + request.getTitle());

        IncidentResponse response = enrich(List.of(saved)).get(0);
        metrics.created(response.severity(), request.getDetectedObject());
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
     * Record the detector run and its output before creating the incident.
     * These rows are deliberately not fire-and-forget: the same database
     * transaction protects the incident, alert and source detection.
     */
    @SuppressWarnings("unchecked")
    private Long recordDetection(IncidentIngestRequest request, Camera camera) {
        Number modelId = (Number) entityManager.createNativeQuery(
                        "SELECT model_id FROM ai_models WHERE model_name = 'CYPHER-YOLOv8s' AND version = '3.0'")
                .getSingleResult();

        long jobId = insertWithId("""
                INSERT INTO inference_jobs
                    (camera_id, model_id, started_at, completed_at, status, frames_processed)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'COMPLETED', 1)
                """, "job_id", camera.getCameraId(), modelId.longValue());

        String object = request.getDetectedObject() == null
                ? "unknown" : request.getDetectedObject().trim().toLowerCase();
        double confidence = request.getConfidence() == null ? 0d : request.getConfidence();
        String box = request.getBoundingBox() == null ? null : request.getBoundingBox().toString();
        return insertWithId("""
                INSERT INTO detections
                    (camera_id, job_id, detected_object, confidence, bounding_box, detected_at)
                VALUES (?, ?, ?, ?, CAST(? AS %s), CURRENT_TIMESTAMP)
                """.formatted(dialect.jsonType()), "detection_id",
                camera.getCameraId(), jobId, object, confidence, box);
    }

    private long insertWithId(String sql, String idColumn, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{idColumn});
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey(), "Missing generated ID").longValue();
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

        // Weapons and fire need review even at a lower confidence score.
        if (CRITICAL_OBJECTS.contains(object)) {
            return new SeverityDecision(SEVERITY_CRITICAL,
                    "'" + object + "' is a high-risk class -> CRITICAL regardless of confidence");
        }
        if (object.equals("smoke") || object.equals("fall") || object.equals("lying")) {
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
