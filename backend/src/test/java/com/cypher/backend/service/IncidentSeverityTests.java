package com.cypher.backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentSeverityTests {
    @Test
    void weaponsRemainCriticalAtLowConfidence() {
        for (String object : new String[]{"knife", "pistol", "fire"}) {
            assertEquals("CRITICAL", IncidentService.deriveSeverity(object, 38d).severity());
        }
    }

    @Test
    void fallThresholdIsInclusive() {
        assertEquals("HIGH", IncidentService.deriveSeverity("lying", 85d).severity());
        assertEquals("MEDIUM", IncidentService.deriveSeverity("lying", 84.9d).severity());
    }

    @Test
    void missingDetectionDoesNotBecomeCritical() {
        assertEquals("LOW", IncidentService.deriveSeverity(null, null).severity());
    }
}
