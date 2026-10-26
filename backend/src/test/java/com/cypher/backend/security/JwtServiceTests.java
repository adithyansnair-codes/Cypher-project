package com.cypher.backend.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {
    private JwtService service(long expiration) {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", Base64.getEncoder().encodeToString(bytes));
        ReflectionTestUtils.setField(service, "expiration", expiration);
        service.init();
        return service;
    }

    @Test
    void tokenPreservesSubjectAndRole() {
        JwtService service = service(60000);
        String token = service.generateToken("ci@example.test", "OPERATOR");
        assertTrue(service.isTokenValid(token, "ci@example.test"));
        assertFalse(service.isTokenValid(token, "other@example.test"));
        assertEquals("OPERATOR", service.extractRole(token));
    }

    @Test
    void expiredAndForeignSignaturesAreRejected() {
        JwtService expired = service(-60000);
        assertFalse(expired.isTokenValid(expired.generateToken("ci@example.test"), "ci@example.test"));
        JwtService first = service(60000);
        JwtService second = service(60000);
        assertFalse(second.isTokenValid(first.generateToken("ci@example.test"), "ci@example.test"));
        assertFalse(first.isTokenValid("invalid-token", "ci@example.test"));
    }

    @Test
    void shortSigningKeyIsRejected() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", Base64.getEncoder().encodeToString(new byte[8]));
        assertThrows(IllegalStateException.class, service::init);
    }
}
