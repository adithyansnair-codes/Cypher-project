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

                        // Public: the operator cockpit and its assets. These are
                        // static files; the data they display still comes from
                        // /api/**, which requires a token.
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/styles.css",
                                "/favicon.ico", "/manifest.json",
                                "/*.js", "/*.css", "/*.png", "/*.svg",
                                "/ml-demo/**").permitAll()

                        // Public: liveness probe.
                        .requestMatchers("/health").permitAll()

                        // Public at the HTTP layer: the WebSocket handshake is
                        // authenticated by JwtHandshakeInterceptor instead,
                        // because a browser cannot set an Authorization header
                        // on it (MoSCoW M-03).
                        .requestMatchers("/ws/**").permitAll()

                        // -------------------------------------------------- RBAC --
                        // MoSCoW S-05. Roles arrive in the JWT's `role` claim and
                        // become ROLE_<role> authorities. This matrix mirrors the
                        // role_permissions rows seeded by V2 and completed by V4.

                        // Administration: the user directory and the audit trail.
                        .requestMatchers("/users/**", "/api/audit/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN")

                        // The operator workflow: acknowledge and resolve.
                        .requestMatchers(HttpMethod.POST,
                                "/api/incidents/*/acknowledge", "/api/incidents/*/resolve")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "SECURITY_MANAGER", "OPERATOR")

                        // Detection ingest is machine-to-machine; the AI engine
                        // signs in with an operator-grade service account.
                        .requestMatchers(HttpMethod.POST, "/api/incidents/ingest", "/api/trace")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "SECURITY_MANAGER")

                        // Exports: the roles whose seeded permission set includes
                        // REPORT_READ. VIEWER deliberately does not.
                        .requestMatchers("/api/reports/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "SECURITY_MANAGER", "ANALYST", "OPERATOR")

                        // Asset administration (S-07): anyone who owns cameras
                        // may edit them, but only ADMIN may remove one.
                        .requestMatchers(HttpMethod.POST, "/api/cameras/**", "/api/projects/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "SECURITY_MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/cameras/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "SECURITY_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/cameras/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN")

                        // Reads stay open to every authenticated role, VIEWER
                        // included: dashboard, incidents, cameras, reports, /api/me.

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
