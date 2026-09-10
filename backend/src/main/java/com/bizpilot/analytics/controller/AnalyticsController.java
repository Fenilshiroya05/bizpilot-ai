package com.bizpilot.analytics.controller;

import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
import com.bizpilot.analytics.dto.LeadFunnelStageResponse;
import com.bizpilot.analytics.dto.LeadSourceBreakdownResponse;
import com.bizpilot.analytics.dto.RevenueTrendPointResponse;
import com.bizpilot.analytics.dto.SalesPipelineStageResponse;
import com.bizpilot.analytics.dto.TopCustomerResponse;
import com.bizpilot.analytics.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Analytics (Phase 19, CLAUDE.md §22). Deliberately thin — all aggregation
 * logic lives in {@link AnalyticsService}; the organization is always
 * resolved server-side from the authenticated tenant context, never
 * accepted from the client in any form.
 *
 * <p>The Phase 26 chart/widget endpoints below follow the exact same
 * pattern as {@link #summary()}: no request parameters, same {@code
 * ANALYTICS_READ} permission, organization resolved server-side only.
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

    @GetMapping("/revenue-trend")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public List<RevenueTrendPointResponse> revenueTrend() {
        return analyticsService.getRevenueTrend();
    }

    @GetMapping("/lead-funnel")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public List<LeadFunnelStageResponse> leadFunnel() {
        return analyticsService.getLeadFunnel();
    }

    @GetMapping("/lead-sources")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public List<LeadSourceBreakdownResponse> leadSources() {
        return analyticsService.getLeadSources();
    }

    @GetMapping("/sales-pipeline")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public List<SalesPipelineStageResponse> salesPipeline() {
        return analyticsService.getSalesPipeline();
    }

    @GetMapping("/top-customers")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public List<TopCustomerResponse> topCustomers() {
        return analyticsService.getTopCustomers();
    }
}
