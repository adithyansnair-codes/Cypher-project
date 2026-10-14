package com.cypher.backend.controller;

import com.cypher.backend.dto.response.AuditEntryResponse;
import com.cypher.backend.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The audit trail (MoSCoW S-06).
 *
 * <p>Restricted to SUPER_ADMIN and ADMIN in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> recent(@RequestParam(defaultValue = "100") int limit) {
        return auditService.recent(limit);
    }
}
