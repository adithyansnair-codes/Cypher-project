package com.cypher.backend.dto.response;

import com.cypher.backend.entity.User;

/**
 * The signed-in operator's own profile.
 *
 * <p>The cockpit uses this to show who is signed in and to hide actions the
 * role cannot perform. The server still enforces them; see
 * {@code SecurityConfig}.
 */
public record ProfileResponse(
        Long userId,
        String fullName,
        String email,
        String role,
        String status
) {

    public static ProfileResponse of(User user) {
        return new ProfileResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRoleName(),
                user.getStatus());
    }
}
