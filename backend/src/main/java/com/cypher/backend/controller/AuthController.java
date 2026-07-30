package com.cypher.backend.controller;
import com.cypher.backend.dto.request.RegisterRequest;
import com.cypher.backend.dto.request.LoginRequest;
import com.cypher.backend.security.JwtService;
import com.cypher.backend.dto.response.RegisterResponse;
import com.cypher.backend.dto.response.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.cypher.backend.entity.User;
import com.cypher.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService,
                      JwtService jwtService) {

    this.userService = userService;
    this.jwtService = jwtService;
}
    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {

        User user = new User();

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword());
        user.setPhone(request.getPhone());
        user.setStatus("ACTIVE");

        User savedUser = userService.saveUser(user);

        return new RegisterResponse(
                "Registration successful",
                savedUser.getUserId(),
                savedUser.getEmail()
        );
    }
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {

    boolean success = userService.login(request);

    if (success) {
        String token = jwtService.generateToken(
        request.getEmail()
);

return ResponseEntity.ok(
        new LoginResponse(token)
);
    }

    return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(new LoginResponse("Invalid email or password"));
}
}
