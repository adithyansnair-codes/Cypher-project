package com.cypher.backend.controller;

import com.cypher.backend.dto.response.DashboardStatsResponse;
import com.cypher.backend.dto.response.IncidentResponse;
import com.cypher.backend.entity.Camera;
import com.cypher.backend.repository.CameraRepository;
import com.cypher.backend.repository.UserRepository;
import com.cypher.backend.service.IncidentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Incident read model and the operator acknowledge/resolve workflow.
 *
 * <p>All routes require a bearer token: {@code SecurityConfig} permits only
 * {@code /auth/**} anonymously.
 */
@RestController
@RequestMapping("/api")
public class IncidentController {

    private final IncidentService incidentService;
    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;

    public IncidentController(IncidentService incidentService,
                              CameraRepository cameraRepository,
                              UserRepository userRepository) {
        this.incidentService = incidentService;
        this.cameraRepository = cameraRepository;
        this.userRepository = userRepository;
    }

    /**
     * Incident stream for the cockpit.
     *
     * @param status   optional filter: OPEN | ACKNOWLEDGED | RESOLVED
     * @param severity optional filter: LOW | MEDIUM | HIGH | CRITICAL
     */
    @GetMapping("/incidents")
    public List<IncidentResponse> listIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        return incidentService.list(status, severity);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable Long id) {
        return incidentService.get(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** OPEN -> ACKNOWLEDGED. Assigns the incident to the calling operator. */
    @PostMapping("/incidents/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(@PathVariable Long id, Authentication auth) {
        return act(() -> incidentService.acknowledge(id, operatorId(auth)));
    }

    /** OPEN or ACKNOWLEDGED -> RESOLVED. Also closes the incident's alerts. */
    @PostMapping("/incidents/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable Long id, Authentication auth) {
        return act(() -> incidentService.resolve(id, operatorId(auth)));
    }

    /** The counters shown across the top of the cockpit. */
    @GetMapping("/dashboard/stats")
    public DashboardStatsResponse stats() {
        return incidentService.stats();
    }

    /** Monitored assets, for the sidebar. */
    @GetMapping("/cameras")
    public List<Camera> listCameras() {
        return cameraRepository.findAllByOrderByCameraNameAsc();
    }

    // -----------------------------------------------------------------------

    /** Shared error mapping so both workflow endpoints behave identically. */
    private ResponseEntity<?> act(java.util.function.Supplier<IncidentResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            // unknown incident id
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // wrong state, e.g. resolving something already resolved
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Resolves the authenticated operator's numeric id from the JWT subject,
     * which is their email. Returns null if the user row has since been deleted,
     * in which case the incident is still actioned but left unassigned.
     */
    private Long operatorId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName())
                .map(u -> u.getUserId())
                .orElse(null);
    }
}
