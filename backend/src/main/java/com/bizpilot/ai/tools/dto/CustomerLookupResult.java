package com.bizpilot.ai.tools.dto;

/**
 * Uniform wrapper for {@code getCustomer}/single-record tool lookups (Phase
 * 17, project instructions §18) — a tool call has no HTTP layer to express
 * "404" with, so a legitimate "not found in your organization" outcome
 * (including a cross-tenant id — indistinguishable by design, mirroring
 * {@code CustomerNotFoundException}'s own existing rationale) is a normal,
 * successful tool result, never an exception. Exactly one of {@code
 * customer}/{@code message} is populated.
 */
public record CustomerLookupResult(boolean found, CustomerToolResult customer, String message) {

    public static CustomerLookupResult of(CustomerToolResult customer) {
        return new CustomerLookupResult(true, customer, null);
    }

    public static CustomerLookupResult notFound() {
        return new CustomerLookupResult(false, null, "No customer found with that ID in your organization.");
    }
}
