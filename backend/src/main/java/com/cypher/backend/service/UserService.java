package com.cypher.backend.service;

import com.cypher.backend.dto.request.LoginRequest;
import com.cypher.backend.dto.request.RegisterRequest;
import com.cypher.backend.entity.Role;
import com.cypher.backend.entity.User;
import com.cypher.backend.repository.RoleRepository;
import com.cypher.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    /**
     * Role granted to self-registered users. Elevated roles are assigned by an
     * administrator, never by the registration endpoint.
     */
    private static final String DEFAULT_ROLE = "VIEWER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Registers a new user.
     *
     * <p>The caller supplies a PLAINTEXT password; this method hashes it. The
     * previous implementation hashed in the controller and then hashed the
     * resulting hash again here, so no stored password could ever match.
     *
     * @throws IllegalArgumentException if the email is already registered
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role role = roleRepository.findByRoleName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Role '" + DEFAULT_ROLE + "' is missing. Run the Flyway seed migration."));

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setStatus("ACTIVE");
        user.setRole(role);

        return userRepository.save(user);
    }

    /**
     * Verifies credentials. {@code passwordEncoder.matches} is constant-time with
     * respect to the hash comparison.
     */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .filter(user -> passwordEncoder.matches(
                        request.getPassword(), user.getPasswordHash()))
                .filter(user -> "ACTIVE".equals(user.getStatus()));
    }
}
