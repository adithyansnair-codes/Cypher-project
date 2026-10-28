package com.cypher.backend.repository;

import com.cypher.backend.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByIncidentIdOrderByUploadedAtDesc(Long incidentId);

    Optional<Media> findByMediaIdAndIncidentId(Long mediaId, Long incidentId);
}
