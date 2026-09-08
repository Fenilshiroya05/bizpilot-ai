package com.bizpilot.ai.tools.dto;

/** Uniform wrapper for {@code getLead} — same rationale as {@link CustomerLookupResult}. */
public record LeadLookupResult(boolean found, LeadToolResult lead, String message) {

    public static LeadLookupResult of(LeadToolResult lead) {
        return new LeadLookupResult(true, lead, null);
    }

    public static LeadLookupResult notFound() {
        return new LeadLookupResult(false, null, "No lead found with that ID in your organization.");
    }
}
