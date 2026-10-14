package com.cypher.gateway.security;

import com.cypher.gateway.model.UserEntity;
import com.cypher.gateway.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            String email = jwtService.extractEmail(token);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UserEntity user = users.findByEmailIgnoreCase(email).orElse(null);

                if (user != null && jwtService.isTokenValid(token, user.getEmail())) {

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user.getEmail(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                            );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (Exception ignored) {
            // Invalid token; request will be rejected by Spring Security.
        }

        filterChain.doFilter(request, response);
    }
}
