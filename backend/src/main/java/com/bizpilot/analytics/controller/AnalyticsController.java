package com.bizpilot.analytics.controller;

import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
import com.bizpilot.analytics.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics (Phase 19, CLAUDE.md §22). Deliberately thin — all aggregation
 * logic lives in {@link AnalyticsService}; the organization is always
 * resolved server-side from the authenticated tenant context, never
 * accepted from the client in any form.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public AnalyticsSummaryResponse summary() {
        return analyticsService.getSummary();
    }
}
