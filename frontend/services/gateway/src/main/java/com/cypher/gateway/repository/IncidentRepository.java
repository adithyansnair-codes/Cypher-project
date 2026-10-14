package com.cypher.gateway.repository;

import com.cypher.gateway.model.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    long countByStatus(String status);

    long countByPriority(String priority);

    List<IncidentEntity> findAllByOrderByCreatedAtDesc();
}
