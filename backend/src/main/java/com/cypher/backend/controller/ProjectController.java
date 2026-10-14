package com.cypher.backend.controller;

import com.cypher.backend.dto.request.ProjectRequest;
import com.cypher.backend.entity.Project;
import com.cypher.backend.repository.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Project administration (MoSCoW S-07). Cameras hang off a project, so creating
 * one is the first step before any asset can be registered.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public List<Project> list() {
        return projectRepository.findAllByOrderByProjectNameAsc();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ProjectRequest request) {
        String name = request.projectName().trim();
        if (projectRepository.existsByProjectName(name)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "A project called '" + name + "' already exists"));
        }

        Project project = new Project();
        project.setProjectName(name);
        project.setDescription(request.description());
        project.setLocation(request.location());
        project.setStatus("ACTIVE");
        return ResponseEntity.status(HttpStatus.CREATED).body(projectRepository.save(project));
    }
}
