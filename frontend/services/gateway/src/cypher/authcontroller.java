package com.cypher.gateway.api;

import com.cypher.gateway.model.UserEntity;
import com.cypher.gateway.repository.UserRepository;
import com.cypher.gateway.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.framework.http.HttpStatus;
import org.springframework.framework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }
}
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = users.findByEmailIgnoreCase(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid email or password"));
        }
        return ResponseEntity.ok(new LoginResponse(
                jwtService.createToken(user.getEmail(), user.getRole(), user.getDisplayName()),
                new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole())));
    }
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }
    public record LoginResponse(String token, UserView user) { }
    public record UserView(Long id, String email, String displayName, String role) { }
    public record ErrorResponse(String message) { }
}
