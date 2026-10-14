package com.cypher.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creating or updating a monitored camera (MoSCoW S-07).
 */
public record CameraRequest(

        @NotNull(message = "projectId is required")
        Long projectId,

        @NotBlank(message = "cameraName is required")
        @Size(max = 100, message = "cameraName must be at most 100 characters")
        String cameraName,

        @NotBlank(message = "cameraCode is required")
        @Size(max = 50, message = "cameraCode must be at most 50 characters")
        String cameraCode,

        @Size(max = 200, message = "location must be at most 200 characters")
        String location,

        /** RTSP / MJPEG / device path the AI engine can open. */
        String streamUrl,

        /** ONLINE | OFFLINE | MAINTENANCE; defaults to OFFLINE. */
        String cameraStatus
) {
}
