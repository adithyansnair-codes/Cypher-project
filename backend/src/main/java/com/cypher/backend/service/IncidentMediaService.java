package com.cypher.backend.service;

import com.cypher.backend.entity.Media;
import com.cypher.backend.repository.IncidentRepository;
import com.cypher.backend.repository.MediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IncidentMediaService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "video/mp4"
    );

    private final IncidentRepository incidents;
    private final MediaRepository mediaRepository;
    private final S3StorageService storage;

    public IncidentMediaService(
            IncidentRepository incidents,
            MediaRepository mediaRepository,
            S3StorageService storage) {
        this.incidents = incidents;
        this.mediaRepository = mediaRepository;
        this.storage = storage;
    }

    @Transactional
    public Map<String, Object> upload(Long incidentId, MultipartFile file) throws IOException {
        if (!storage.isEnabled()) {
            throw new IllegalStateException(
                    "S3 storage is not configured. Set S3_BUCKET before uploading media.");
        }
        if (!incidents.existsById(incidentId)) {
            throw new IllegalArgumentException("No incident with id " + incidentId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String contentType = file.getContentType() == null
                ? "application/octet-stream" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("unsupported media type: " + contentType);
        }

        String original = file.getOriginalFilename() == null
                ? "upload" : file.getOriginalFilename();
        String clean = original.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1);
        if (clean.isBlank() || clean.contains("..")) {
            throw new IllegalArgumentException("invalid file name");
        }

        String key = "incident-" + incidentId + "/" + UUID.randomUUID() + "-" + clean;
        byte[] bytes = file.getBytes();
        String storedPath = storage.upload(key, bytes, contentType);
        try {
            Media media = new Media();
            media.setIncidentId(incidentId);
            media.setMediaType(contentType.startsWith("image/") ? "IMAGE" : "VIDEO");
            media.setFileName(clean);
            media.setFilePath(storedPath);
            media.setFileSize((long) bytes.length);
            Media saved = mediaRepository.save(media);
            return response(saved);
        } catch (RuntimeException ex) {
            storage.delete(storedPath);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long incidentId) {
        if (!incidents.existsById(incidentId)) {
            throw new IllegalArgumentException("No incident with id " + incidentId);
        }
        return mediaRepository.findByIncidentIdOrderByUploadedAtDesc(incidentId)
                .stream().map(this::response).toList();
    }

    @Transactional
    public void delete(Long incidentId, Long mediaId) {
        Media media = mediaRepository.findByMediaIdAndIncidentId(mediaId, incidentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No media " + mediaId + " for incident " + incidentId));
        storage.delete(media.getFilePath());
        mediaRepository.delete(media);
    }

    private Map<String, Object> response(Media media) {
        return Map.of(
                "mediaId", media.getMediaId(),
                "incidentId", media.getIncidentId(),
                "mediaType", media.getMediaType(),
                "fileName", media.getFileName(),
                "fileSize", media.getFileSize(),
                "downloadUrl", storage.downloadUrl(media.getFilePath())
        );
    }
}
