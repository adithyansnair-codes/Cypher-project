package com.cypher.backend.config;

import com.cypher.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Stateless API with a bearer token: there is no session cookie for an
                // attacker to ride, so CSRF protection does not apply.
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public: registration and login.
                        .requestMatchers("/auth/**").permitAll()

                        // Public: the error dispatch. Without this, a validation failure
                        // (e.g. HTTP 400 from @Valid) is re-dispatched to /error, which
                        // is itself authenticated, and the client sees 403 instead of
                        // the real status and message.
                        .requestMatchers("/error").permitAll()

                        // Public: liveness probe.
                        .requestMatchers(HttpMethod.GET, "/").permitAll()

                        // Everything else needs a valid bearer token.
                        .anyRequest().authenticated())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 10 matches the hashes produced by the seed migration.
        return new BCryptPasswordEncoder();
    }
}
