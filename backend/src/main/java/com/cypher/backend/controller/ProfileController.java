package com.cypher.backend.controller;

import com.cypher.backend.dto.response.ProfileResponse;
import com.cypher.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The signed-in operator's own profile (the JWT carries only email and role).
 *
 * <p>The cockpit uses this to name the person in the sidebar and to hide the
 * actions their role cannot perform.
 */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "not authenticated"));
        }
        return userRepository.findByEmail(auth.getName())
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(ProfileResponse.of(user)))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "no such user")));
    }
}
