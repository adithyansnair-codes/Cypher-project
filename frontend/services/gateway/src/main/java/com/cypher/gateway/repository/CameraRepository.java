package com.cypher.gateway.repository;

import com.cypher.gateway.model.CameraEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CameraRepository extends JpaRepository<CameraEntity, Long> {

    List<CameraEntity> findAllByOrderByNameAsc();
}
