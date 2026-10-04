package com.cypher.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The contract the AI engine uses to report a detection.
 *
 * <p>This is MoSCoW requirement M-05: a stable interface between the vision
 * pipeline and the gateway. The Python service will POST this shape; nothing
 * about the gateway changes when the detector does.
 *
 * <p>{@code severity} is optional. When omitted it is derived from the detected
 * object and confidence, so a new detector cannot invent its own priority
 * vocabulary.
 */
public class IncidentIngestRequest {

    @jakarta.validation.constraints.NotNull(message = "cameraId is required")
    private Long cameraId;

    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title must be at most 200 characters")
    private String title;

    private String description;

    /** Optional. One of LOW, MEDIUM, HIGH, CRITICAL. */
    private String severity;

    /** Optional, e.g. "person", "smoke", "weapon". Used to derive severity. */
    private String detectedObject;

    /** Optional 0-100 confidence from the model. */
    private Double confidence;

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
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

    public String getDetectedObject() {
        return detectedObject;
    }

    public void setDetectedObject(String detectedObject) {
        this.detectedObject = detectedObject;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}
