package com.cypher.backend.service;
import com.cypher.backend.entity.User;
import com.cypher.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.cypher.backend.dto.request.LoginRequest;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User saveUser(User user) {

        user.setPasswordHash(
                passwordEncoder.encode(user.getPasswordHash())
        );

        return userRepository.save(user);
    }
public boolean login(LoginRequest request) {

    Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());

    if (optionalUser.isEmpty()) {
        return false;
    }

    User user = optionalUser.get();

    return passwordEncoder.matches(
            request.getPassword(),
            user.getPasswordHash()
    );
}
}
