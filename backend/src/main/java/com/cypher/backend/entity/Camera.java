package com.cypher.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A monitored camera. Maps the {@code cameras} table.
 *
 * <p>Note the column is {@code camera_status}, not {@code status} -- cameras use
 * ONLINE / OFFLINE / MAINTENANCE.
 */
@Entity
@Table(name = "cameras")
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "camera_name", nullable = false, length = 100)
    private String cameraName;

    @Column(name = "camera_code", nullable = false, length = 50)
    private String cameraCode;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "stream_url", columnDefinition = "text")
    private String streamUrl;

    @Column(name = "camera_status", nullable = false, length = 20)
    private String cameraStatus = "OFFLINE";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Camera() {
    }

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
    }

    public String getCameraCode() {
        return cameraCode;
    }

    public void setCameraCode(String cameraCode) {
        this.cameraCode = cameraCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getCameraStatus() {
        return cameraStatus;
    }

    public void setCameraStatus(String cameraStatus) {
        this.cameraStatus = cameraStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
