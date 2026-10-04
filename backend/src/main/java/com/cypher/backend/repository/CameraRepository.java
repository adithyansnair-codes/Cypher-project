package com.cypher.backend.repository;

import com.cypher.backend.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CameraRepository extends JpaRepository<Camera, Long> {

    List<Camera> findAllByOrderByCameraNameAsc();

    List<Camera> findByProjectIdOrderByCameraNameAsc(Long projectId);

    Optional<Camera> findByCameraCode(String cameraCode);

    long countByCameraStatus(String cameraStatus);
}
