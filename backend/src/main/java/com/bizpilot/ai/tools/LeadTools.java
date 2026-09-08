package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.LeadLookupResult;
import com.bizpilot.ai.tools.dto.LeadToolResult;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.exception.LeadNotFoundException;
import com.bizpilot.sales.service.LeadSearchCriteria;
import com.bizpilot.sales.service.LeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only AI tools over the organization's leads (Phase 17, CLAUDE.md
 * §19) — mirrors {@link CustomerTools}'s exact structure/rationale.
 */
@Component
public class LeadTools {

    private static final Logger log = LoggerFactory.getLogger(LeadTools.class);

    private static final int MAX_RESULTS = 5;
    private static final int MAX_QUERY_LENGTH = 200;

    private final LeadService leadService;

    public LeadTools(LeadService leadService) {
        this.leadService = leadService;
    }

    @Tool(description = "Search the current organization's leads by name, company, or email. "
            + "Returns at most 5 matches.")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public List<LeadToolResult> searchLeads(
            @ToolParam(description = "Free-text search term (matched against name, company, and email)")
            String query) {
        Instant start = Instant.now();
        String normalized = normalizeQuery(query);
        LeadSearchCriteria criteria = new LeadSearchCriteria(null, null, null, null, false, null, false, normalized);
        Page<Lead> page = leadService.search(criteria, PageRequest.of(0, MAX_RESULTS));
        List<LeadToolResult> results = page.getContent().stream().map(LeadTools::toResult).toList();
        logOutcome("searchLeads", start, results.size());
        return results;
    }

    @Tool(description = "Look up one lead by its exact ID within the current organization.")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public LeadLookupResult getLead(@ToolParam(description = "Lead UUID") UUID leadId) {
        Instant start = Instant.now();
        if (leadId == null) {
            throw new IllegalArgumentException("leadId must be a valid UUID.");
        }
        try {
            Lead lead = leadService.getById(leadId);
            logOutcome("getLead", start, 1);
            return LeadLookupResult.of(toResult(lead));
        } catch (LeadNotFoundException e) {
            logOutcome("getLead", start, 0);
            return LeadLookupResult.notFound();
        }
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank.");
        }
        String trimmed = query.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    private static void logOutcome(String toolName, Instant start, int resultCount) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("AI tool call succeeded [tool={}, resultCount={}, durationMs={}]", toolName, resultCount,
                durationMs);
    }

    private static LeadToolResult toResult(Lead lead) {
        return new LeadToolResult(
                lead.getId(),
                lead.getName(),
                lead.getCompany(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getStatus(),
                lead.getSource(),
                lead.getPriority(),
                lead.getFollowUpDate(),
                lead.getAssignedToUserId()
        );
    }
}
