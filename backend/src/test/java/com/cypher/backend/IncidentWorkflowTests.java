package com.cypher.backend;

import com.cypher.backend.dto.request.IncidentIngestRequest;
import com.cypher.backend.service.AnalyticsService;
import com.cypher.backend.service.AuditService;
import com.cypher.backend.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IncidentWorkflowTests extends DatabaseTest {
    @Autowired IncidentService incidents;
    @Autowired AnalyticsService analytics;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private IncidentIngestRequest request(long camera, String title) {
        var request = new IncidentIngestRequest();
        request.setCameraId(camera);
        request.setTitle(title);
        request.setDetectedObject("knife");
        request.setConfidence(82.5);
        request.setBoundingBox(List.of(10d, 20d, 50d, 80d));
        return request;
    }

    @Test
    void ingestResolveAndReadAudit() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long camera = TestData.camera(jdbc);
            var detection = request(camera, "Workflow test");
            detection.setSeverity("LOW");
            var result = incidents.ingest(detection, null);
            assertEquals("CRITICAL", result.severity());
            assertEquals(1L, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM incidents i
                    JOIN detections d ON d.detection_id = i.detection_id
                    JOIN inference_jobs j ON j.job_id = d.job_id
                    JOIN alerts a ON a.incident_id = i.incident_id
                    WHERE i.incident_id = ? AND d.detected_object = 'knife' AND j.status = 'COMPLETED'
                    """, Long.class, result.incidentId()));
            incidents.acknowledge(result.incidentId(), null);
            incidents.resolve(result.incidentId(), null);
            jdbc.queryForObject("SELECT COUNT(*) FROM incidents", Long.class);
            // Flush the JPA update before reading the trigger results through JDBC.
            analytics.summary();
            assertEquals("CLOSED", jdbc.queryForObject(
                    "SELECT alert_status FROM alerts WHERE incident_id = ?", String.class, result.incidentId()));
            assertTrue(audit.recent(50).stream().anyMatch(entry ->
                    result.incidentId().equals(entry.entityId()) && "RESOLVED".equals(entry.status())));
            assertThrows(IllegalStateException.class, () -> incidents.resolve(result.incidentId(), null));
            tx.setRollbackOnly();
        });
    }

    @Test
    void failedIngestRollsBackAllFourRows() {
        long camera = TestData.camera(jdbc);
        long[] before = counts();
        assertThrows(RuntimeException.class, () -> incidents.ingest(request(camera, null), null));
        assertArrayEquals(before, counts());
    }

    @Test
    void concurrentIngestReturnsItsOwnIncident() throws Exception {
        long camera = TestData.camera(jdbc);
        String prefix = "Concurrent test " + UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 12).mapToObj(index ->
                    executor.submit(() -> {
                        String title = prefix + " " + index;
                        var result = incidents.ingest(request(camera, title), null);
                        assertEquals(title, result.title());
                        return result.incidentId();
                    })).toList();
            var ids = new java.util.HashSet<Long>();
            for (var task : tasks) assertTrue(ids.add(task.get()));
        } finally {
            jdbc.update("DELETE FROM incidents WHERE title LIKE ?", prefix + "%");
        }
    }

    @Test
    void analyticsIncludesNewDetectionInUtcDay() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long camera = TestData.camera(jdbc);
            var before = analytics.summary();
            incidents.ingest(request(camera, "Analytics test"), null);
            var after = analytics.summary();
            assertEquals(before.detectionsTotal() + 1, after.detectionsTotal());
            assertEquals(before.detectionsLast7Days() + 1, after.detectionsLast7Days());
            assertEquals(before.incidentsTotal() + 1, after.incidentsTotal());
            assertTrue(after.incidentsLast7Days().stream().anyMatch(day ->
                    day.day().equals(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString())));
            tx.setRollbackOnly();
        });
    }

    private long[] counts() {
        return java.util.stream.Stream.of("inference_jobs", "detections", "incidents", "alerts")
                .mapToLong(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).toArray();
    }
}
