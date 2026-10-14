package com.cypher.gateway.api;

import com.cypher.gateway.model.CameraEntity;
import com.cypher.gateway.model.DetectionEntity;
import com.cypher.gateway.model.IncidentEntity;
import com.cypher.gateway.repository.CameraRepository;
import com.cypher.gateway.repository.DetectionRepository;
import com.cypher.gateway.repository.IncidentRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final CameraRepository cameras;
    private final DetectionRepository detections;
    private final IncidentRepository incidents;
    private final SimpMessagingTemplate messaging;
    private final String serviceApiKey;

    public IngestionController(
            CameraRepository cameras,
            DetectionRepository detections,
            IncidentRepository incidents,
            SimpMessagingTemplate messaging,
            @Value("${cypher.service-api-key}") String serviceApiKey) {

        this.cameras = cameras;
        this.detections = detections;
        this.incidents = incidents;
        this.messaging = messaging;
        this.serviceApiKey = serviceApiKey;
    }

    @PostMapping("/incident")
    @Transactional
    public ResponseEntity<?> ingest(
            @RequestHeader(
                    value = "X-Service-Key",
                    required = false
            ) String key,
            @Valid @RequestBody IncidentIngestRequest request) {

        if (!serviceApiKey.equals(key)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid service key"));
        }

        CameraEntity camera =
                cameras.findById(request.cameraId()).orElse(null);

        if (camera == null) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            "Camera not found: " + request.cameraId()
                    ));
        }

        List<DetectionInput> detectionInputs =
                request.detections() == null
                        ? List.of()
                        : request.detections();

        for (DetectionInput input : detectionInputs) {

            DetectionEntity detection = new DetectionEntity();

            detection.setCamera(camera);
            detection.setAssetType(input.assetType());
            detection.setConfidence(input.confidence());

            detection.setBoundingBox(
                    input.boundingBox() == null
                            ? new HashMap<>()
                            : input.boundingBox()
            );

            detection.setMetadata(
                    input.metadata() == null
                            ? new HashMap<>()
                            : input.metadata()
            );

            detection.setDetectedAt(Instant.now());

            detections.save(detection);
        }

        IncidentEntity incident = new IncidentEntity();

        incident.setCamera(camera);
        incident.setIncidentType(
                request.incidentType().toUpperCase()
        );
 if (request.priority() != null) {
            incident.setPriority(request.priority().toUpperCase());
        }

        incident.setAssetType(
                request.assetType().toUpperCase()
        );
        incident.setAssetType(
                request.assetType().toUpperCase()
        );
        incident.setConfidence(request.confidence());
        incident.setSummary(request.summary());
        incident.setSnapshotUrl(request.snapshotUrl());

        incident.setBoundingBox(
                request.boundingBox() == null
                        ? new HashMap<>()
                        : request.boundingBox()
        );

        incident.setMetadata(
                request.metadata() == null
                        ? new HashMap<>()
                        : request.metadata()
        );

        incident.setCreatedAt(Instant.now());

        IncidentEntity saved = incidents.save(incident);

        IncidentController.IncidentView view =
                IncidentController.IncidentView.from(saved);

        messaging.convertAndSend(
                "/topic/incidents",
                view
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(view);
    }

    public record IncidentIngestRequest(
            @NotNull Long cameraId,
            @NotBlank String incidentType,
            String priority,
            @NotBlank String assetType,
            @NotNull Double confidence,
            @NotBlank String summary,
            String snapshotUrl,
            Map<String, Object> boundingBox,
            Map<String, Object> metadata,
            List<DetectionInput> detections
    ) {
    }

    public record DetectionInput(
            @NotBlank String assetType,
            @NotNull Double confidence,
            Map<String, Object> boundingBox,
            Map<String, Object> metadata
    ) {
    }
}
