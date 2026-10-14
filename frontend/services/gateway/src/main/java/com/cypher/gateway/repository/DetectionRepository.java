package com.cypher.gateway.repository;

import com.cypher.gateway.model.DetectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionRepository extends JpaRepository<DetectionEntity, Long> {
}
