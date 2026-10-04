package com.cypher.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A security incident. Maps the {@code incidents} table.
 *
 * <p>Status flow: OPEN -> ACKNOWLEDGED -> RESOLVED.
 * <p>Severity: LOW | MEDIUM | HIGH | CRITICAL.
 *
 * <p>{@code resolvedAt} is set when the incident is resolved. A database trigger
 * closes the associated alerts at the same time.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "camera_id", nullable = false)
    private Long cameraId;

    @Column(name = "detection_id")
    private Long detectionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Incident() {
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public Long getDetectionId() {
        return detectionId;
    }

    public void setDetectionId(Long detectionId) {
        this.detectionId = detectionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
