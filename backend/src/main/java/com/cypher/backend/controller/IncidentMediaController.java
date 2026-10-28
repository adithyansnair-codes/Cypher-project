package com.cypher.backend.controller;

import com.cypher.backend.service.IncidentMediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents/{incidentId}/media")
public class IncidentMediaController {

    private final IncidentMediaService media;

    public IncidentMediaController(IncidentMediaService media) {
        this.media = media;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @PathVariable Long incidentId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(media.upload(incidentId, file));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "could not read upload"));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long incidentId) {
        try {
            List<Map<String, Object>> files = media.list(incidentId);
            return ResponseEntity.ok(files);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<?> delete(
            @PathVariable Long incidentId,
            @PathVariable Long mediaId) {
        try {
            media.delete(incidentId, mediaId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
