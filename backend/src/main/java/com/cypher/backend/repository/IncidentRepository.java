package com.cypher.backend.repository;

import com.cypher.backend.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatusOrderByOccurredAtDesc(String status);

    List<Incident> findBySeverityOrderByOccurredAtDesc(String severity);

    List<Incident> findByCameraIdOrderByOccurredAtDesc(Long cameraId);

    Page<Incident> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Both filters optional; null means "no filter on this field". */
    @Query("""
            SELECT i FROM Incident i
            WHERE (:status IS NULL OR i.status = :status)
              AND (:severity IS NULL OR i.severity = :severity)
            ORDER BY i.occurredAt DESC
            """)
    List<Incident> search(@Param("status") String status,
                          @Param("severity") String severity);

    long countByStatus(String status);

    long countBySeverity(String severity);

    long countByStatusAndSeverity(String status, String severity);

    /** Counted in the database, not by loading rows into memory. */
    @Query("""
            SELECT COUNT(i) FROM Incident i
            WHERE i.status = :status AND i.resolvedAt >= :since
            """)
    long countResolvedSince(@Param("status") String status, @Param("since") Instant since);
}
