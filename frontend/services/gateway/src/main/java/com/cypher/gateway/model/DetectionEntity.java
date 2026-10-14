package com.cypher.gateway.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "detections")
public class DetectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private CameraEntity camera;

    @Column(name = "asset_type", nullable = false, length = 100)
    private String assetType;

    @Column(nullable = false)
    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box", columnDefinition = "json")
    private Map<String, Object> boundingBox = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    public DetectionEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CameraEntity getCamera() {
        return camera;
    }

    public void setCamera(CameraEntity camera) {
        this.camera = camera;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(Map<String, Object> boundingBox) {
        this.boundingBox = boundingBox;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }
}
