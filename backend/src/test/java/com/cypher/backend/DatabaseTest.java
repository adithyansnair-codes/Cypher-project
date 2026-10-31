package com.cypher.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class DatabaseTest {
    private static final String KEY;
    static {
        byte[] bytes = new byte[64];
        new java.security.SecureRandom().nextBytes(bytes);
        KEY = java.util.Base64.getEncoder().encodeToString(bytes);
    }

    @DynamicPropertySource
    static void testSettings(DynamicPropertyRegistry properties) {
        properties.add("jwt.secret", () -> KEY);
        properties.add("cypher.s3.bucket", () -> "");
    }
}
