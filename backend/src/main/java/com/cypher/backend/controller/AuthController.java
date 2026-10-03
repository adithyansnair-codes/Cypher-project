package com.cypher.backend.controller;

import com.cypher.backend.dto.request.LoginRequest;
import com.cypher.backend.dto.request.RegisterRequest;
import com.cypher.backend.dto.response.LoginResponse;
import com.cypher.backend.dto.response.RegisterResponse;
import com.cypher.backend.entity.User;
import com.cypher.backend.security.JwtService;
import com.cypher.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Registration and login.
 *
 * <p>Both endpoints are permitted without authentication in
 * {@code SecurityConfig}; everything else requires a bearer token.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User saved = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(
                    "Registration successful",
                    saved.getUserId(),
                    saved.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

        Optional<User> authenticated = userService.authenticate(request);

        if (authenticated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Invalid email or password"));
        }

        User user = authenticated.get();
        String token = jwtService.generateToken(user.getEmail(), user.getRoleName());

        return ResponseEntity.ok(new LoginResponse(token));
    }
}
