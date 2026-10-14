package com.cypher.backend.controller;

import com.cypher.backend.dto.response.AnalyticsResponse;
import com.cypher.backend.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics for the cockpit's second view. Readable by every authenticated
 * role, like every other read.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsResponse summary() {
        return analyticsService.summary();
    }
}
