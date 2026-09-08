package com.bizpilot.ai.tools.dto;

import java.util.List;

/**
 * Uniform wrapper for {@code getCustomerHistory} — same not-found
 * discipline as {@link CustomerLookupResult}. {@code activities} is always
 * bounded (project instructions §14/§16 — the fixed page size the tool
 * requests from {@code CustomerService.getHistory}, never an unbounded dump).
 */
public record CustomerHistoryResult(boolean found, List<CustomerActivityToolResult> activities, String message) {

    public static CustomerHistoryResult of(List<CustomerActivityToolResult> activities) {
        return new CustomerHistoryResult(true, activities, null);
    }

    public static CustomerHistoryResult notFound() {
        return new CustomerHistoryResult(false, List.of(), "No customer found with that ID in your organization.");
    }
}
