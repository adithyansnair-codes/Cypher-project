package com.cypher.backend.repository;

import com.cypher.backend.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<Alert> findByAlertStatusOrderByCreatedAtDesc(String alertStatus);

    long countByAlertStatus(String alertStatus);
}
