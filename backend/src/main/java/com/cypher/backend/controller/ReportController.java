package com.cypher.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Report downloads (MoSCoW S-02 CSV, S-03 Excel, S-04 PDF).
 *
 * <p>The C# reporting worker owns the export formats and reads the database
 * itself. This controller is the gateway's proxy to it, so the cockpit keeps
 * talking to one origin with one bearer token, and the worker stays private.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC);

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "csv", "text/csv",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pdf", "application/pdf");

    private final RestClient reportingWorker;

    public ReportController(
            @Value("${cypher.reporting.url:http://127.0.0.1:5001}") String workerUrl) {
        this.reportingWorker = RestClient.builder().baseUrl(workerUrl).build();
    }

    /**
     * Streams one export back to the operator.
     *
     * @param format csv | xlsx | pdf
     */
    @GetMapping("/incidents.{format}")
    public ResponseEntity<?> incidents(
            @PathVariable String format,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "500") int limit) {

        String normalized = format.toLowerCase();
        String contentType = CONTENT_TYPES.get(normalized);
        if (contentType == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "format must be one of csv, xlsx, pdf"));
        }

        try {
            byte[] body = reportingWorker.get()
                    .uri("/reports/incidents.{format}?status={status}&severity={severity}&limit={limit}",
                            normalized, blankToEmpty(status), blankToEmpty(severity), limit)
                    .retrieve()
                    .body(byte[].class);

            if (body == null || body.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "The reporting worker returned an empty file"));
            }

            String fileName = "cypher-incidents-" + STAMP.format(Instant.now()) + "." + normalized;
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(body);

        } catch (RestClientException e) {
            // The worker is optional at runtime: the cockpit still serves the
            // live incident view, so this is a 503 rather than a 500.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "The reporting worker is unavailable",
                            "detail", String.valueOf(e.getMessage())));
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
