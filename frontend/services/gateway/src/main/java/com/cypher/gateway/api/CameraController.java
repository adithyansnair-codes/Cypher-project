package com.cypher.gateway.api;

import com.cypher.gateway.repository.CameraRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
public class CameraController {

    private final CameraRepository cameras;

    public CameraController(CameraRepository cameras) {
        this.cameras = cameras;
    }

    @GetMapping
    public List<CameraView> list() {
        return cameras.findAllByOrderByNameAsc()
                .stream()
                .map(camera -> new CameraView(
                        camera.getId(),
                        camera.getName(),
                        camera.getLocation(),
                        camera.isActive(),
                        camera.getProject().getName()
                ))
                .toList();
    }

    public record CameraView(
            Long id,
            String name,
            String location,
            boolean active,
            String projectName
    ) {
    }
}
