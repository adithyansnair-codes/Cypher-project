package com.cypher.backend.controller;

import com.cypher.backend.dto.request.CameraRequest;
import com.cypher.backend.entity.Camera;
import com.cypher.backend.repository.CameraRepository;
import com.cypher.backend.repository.IncidentRepository;
import com.cypher.backend.repository.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Camera administration (MoSCoW S-07).
 *
 * <p>Reads are open to every authenticated role; writes are ADMIN,
 * SUPER_ADMIN and SECURITY_MANAGER (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/cameras")
public class CameraController {

    private static final Set<String> STATUSES = Set.of("ONLINE", "OFFLINE", "MAINTENANCE");

    private final CameraRepository cameraRepository;
    private final ProjectRepository projectRepository;
    private final IncidentRepository incidentRepository;

    public CameraController(CameraRepository cameraRepository,
                            ProjectRepository projectRepository,
                            IncidentRepository incidentRepository) {
        this.cameraRepository = cameraRepository;
        this.projectRepository = projectRepository;
        this.incidentRepository = incidentRepository;
    }

    @GetMapping
    public List<Camera> list(@RequestParam(required = false) Long projectId) {
        return projectId == null
                ? cameraRepository.findAllByOrderByCameraNameAsc()
                : cameraRepository.findByProjectIdOrderByCameraNameAsc(projectId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Camera> get(@PathVariable Long id) {
        return cameraRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CameraRequest request) {
        if (!projectRepository.existsById(request.projectId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No project with id " + request.projectId()));
        }
        if (cameraRepository.findByCameraCode(request.cameraCode().trim()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Camera code '" + request.cameraCode().trim() + "' is already in use"));
        }
        String status = normalizeStatus(request.cameraStatus());
        if (status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", statusError()));
        }

        Camera camera = new Camera();
        apply(camera, request, status);
        Camera saved = cameraRepository.save(camera);
        // created_at / updated_at are database defaults, so the row is re-read
        // to return them instead of null.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cameraRepository.findById(saved.getCameraId()).orElse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody CameraRequest request) {
        Camera camera = cameraRepository.findById(id).orElse(null);
        if (camera == null) {
            return ResponseEntity.notFound().build();
        }
        if (!projectRepository.existsById(request.projectId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No project with id " + request.projectId()));
        }
        cameraRepository.findByCameraCode(request.cameraCode().trim())
                .filter(other -> !other.getCameraId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(
                            "Camera code '" + request.cameraCode().trim() + "' is already in use");
                });

        String status = normalizeStatus(request.cameraStatus());
        if (status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", statusError()));
        }

        apply(camera, request, status);
        Camera saved = cameraRepository.save(camera);
        return ResponseEntity.ok(cameraRepository.findById(saved.getCameraId()).orElse(saved));
    }

    /**
     * Removes a camera that never saw an incident.
     *
     * <p>Incidents reference their camera, so a camera with history cannot be
     * deleted without losing the record. Such an asset is taken offline
     * instead, which is also the operationally honest thing to do.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Camera camera = cameraRepository.findById(id).orElse(null);
        if (camera == null) {
            return ResponseEntity.notFound().build();
        }
        long incidents = incidentRepository.countByCameraId(id);
        if (incidents > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Camera " + camera.getCameraCode() + " has " + incidents
                            + " incident(s); set it OFFLINE instead of deleting it"));
        }
        cameraRepository.delete(camera);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------

    private void apply(Camera camera, CameraRequest request, String status) {
        camera.setProjectId(request.projectId());
        camera.setCameraName(request.cameraName().trim());
        camera.setCameraCode(request.cameraCode().trim());
        camera.setLocation(blankToNull(request.location()));
        camera.setStreamUrl(blankToNull(request.streamUrl()));
        camera.setCameraStatus(status);
    }

    private static String normalizeStatus(String value) {
        String status = (value == null || value.isBlank())
                ? "OFFLINE"
                : value.trim().toUpperCase(Locale.ROOT);
        return STATUSES.contains(status) ? status : null;
    }

    private static String statusError() {
        return "cameraStatus must be one of ONLINE, OFFLINE, MAINTENANCE";
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
