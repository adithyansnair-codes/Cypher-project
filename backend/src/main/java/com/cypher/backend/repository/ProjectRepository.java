package com.cypher.backend.repository;

import com.cypher.backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByProjectNameAsc();

    boolean existsByProjectName(String projectName);
}
