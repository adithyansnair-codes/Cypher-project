package com.cypher.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies HS256 JSON Web Tokens.
 *
 * <p>The signing key comes from {@code jwt.secret} (base64) and must be supplied
 * through the environment. The application refuses to start without a valid one,
 * so a development key can never silently reach a deployed environment.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not set. Export JWT_SECRET (base64, 64 random bytes). "
                            + "Generate one with: openssl rand -base64 64");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret must be valid base64", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must decode to at least 32 bytes for HS256; got "
                            + keyBytes.length + ". Generate one with: openssl rand -base64 64");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(CLAIM_ROLE, role == null ? "VIEWER" : role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    /** Kept for callers that have no role to hand. */
    public String generateToken(String email) {
        return generateToken(email, null);
    }

    /** @throws JwtException if the token is malformed, tampered with, or expired. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public String extractUsername(String token) {
        return extractEmail(token);
    }

    public String extractRole(String token) {
        return parse(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * Verifies signature, expiry, and that the subject matches.
     *
     * <p>The previous implementation exposed this method but never called it, so
     * an expired token was still accepted as long as it parsed successfully.
     */
    public boolean isTokenValid(String token, String email) {
        try {
            Claims claims = parse(token);
            return claims.getSubject().equals(email)
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
