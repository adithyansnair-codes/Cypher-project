package com.cypher.gateway.api;

import com.cypher.gateway.model.IncidentEntity;
import com.cypher.gateway.repository.IncidentRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentRepository incidents;

    public IncidentController(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    public List<IncidentView> list() {
        return incidents.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(IncidentView::from)
                .toList();
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {

        IncidentEntity incident = incidents.findById(id).orElse(null);

        if (incident == null) {
            return ResponseEntity.notFound().build();
        }

        String status = request.status().toUpperCase();

        if (!status.equals("OPEN")
                && !status.equals("ACKNOWLEDGED")
                && !status.equals("RESOLVED")) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "Invalid status: " + status)
            );
        }

        incident.setStatus(status);

        if (status.equals("RESOLVED")) {
            incident.setResolvedAt(Instant.now());
        } else {
            incident.setResolvedAt(null);
        }

        IncidentEntity saved = incidents.save(incident);

        return ResponseEntity.ok(IncidentView.from(saved));
    }

    public record StatusRequest(
            @NotBlank String status
    ) {
    }

    public record IncidentView(
            Long id,
            String incidentType,
            String priority,
            String status,
            String assetType,
            double confidence,
            String summary,
            Long cameraId,
            String cameraName,
            String location,
            Map<String, Object> boundingBox,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant resolvedAt
    ) {

        static IncidentView from(IncidentEntity incident) {
            return new IncidentView(
                    incident.getId(),
                    incident.getIncidentType(),
                    incident.getPriority(),
                    incident.getStatus(),
                    incident.getAssetType(),
                    incident.getConfidence(),
                    incident.getSummary(),
                    incident.getCamera().getId(),
                    incident.getCamera().getName(),
                    incident.getCamera().getLocation(),
                    incident.getBoundingBox(),
                    incident.getMetadata(),
                    incident.getCreatedAt(),
                    incident.getResolvedAt()
            );
        }
    }
}