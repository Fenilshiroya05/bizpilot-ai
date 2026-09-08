package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.CustomerActivityToolResult;
import com.bizpilot.ai.tools.dto.CustomerHistoryResult;
import com.bizpilot.ai.tools.dto.CustomerLookupResult;
import com.bizpilot.ai.tools.dto.CustomerToolResult;
import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.entity.CustomerActivity;
import com.bizpilot.crm.exception.CustomerNotFoundException;
import com.bizpilot.crm.service.CustomerService;
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
 * Read-only AI tools over the organization's customers (Phase 17, CLAUDE.md
 * §19). Each method is a thin translation layer — validate/normalize the
 * tool's input, delegate to the existing, already-tenant-scoped {@link
 * CustomerService}, map the result to a small, tool-facing DTO — never a
 * business-logic duplicate of {@code CustomerService} itself.
 *
 * <p><b>No tool here accepts an organization id, tenant id, or any filter
 * beyond what's listed</b> (project instructions §9) — tenant scoping is
 * entirely {@code CustomerService}'s own responsibility, inherited for
 * free, exactly as it already is for every REST call.
 *
 * <p><b>{@code @PreAuthorize} is the actual enforcement mechanism</b>,
 * empirically verified (not assumed) to be respected when Spring AI invokes
 * this bean as a tool — see {@code ToolSecurityEnforcementTest}.
 */
@Component
public class CustomerTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerTools.class);

    /** Locked (project instructions §14): never exposed as a tool parameter. */
    private static final int MAX_RESULTS = 5;
    private static final int MAX_QUERY_LENGTH = 200;

    private final CustomerService customerService;

    public CustomerTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(description = "Search the current organization's customers by name, company, or email. "
            + "Returns at most 5 matches.")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public List<CustomerToolResult> searchCustomers(
            @ToolParam(description = "Free-text search term (matched against name, company, and email)")
            String query) {
        Instant start = Instant.now();
        String normalized = normalizeQuery(query);
        Page<Customer> page = customerService.search(null, normalized, PageRequest.of(0, MAX_RESULTS));
        List<CustomerToolResult> results = page.getContent().stream().map(CustomerTools::toResult).toList();
        logOutcome("searchCustomers", start, results.size());
        return results;
    }

    @Tool(description = "Look up one customer by its exact ID within the current organization.")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CustomerLookupResult getCustomer(@ToolParam(description = "Customer UUID") UUID customerId) {
        Instant start = Instant.now();
        requireNonNull(customerId, "customerId");
        try {
            Customer customer = customerService.getById(customerId);
            logOutcome("getCustomer", start, 1);
            return CustomerLookupResult.of(toResult(customer));
        } catch (CustomerNotFoundException e) {
            logOutcome("getCustomer", start, 0);
            return CustomerLookupResult.notFound();
        }
    }

    @Tool(description = "Return the recent activity/notes/history timeline for one customer, most recent first. "
            + "Returns at most 5 entries.")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CustomerHistoryResult getCustomerHistory(@ToolParam(description = "Customer UUID") UUID customerId) {
        Instant start = Instant.now();
        requireNonNull(customerId, "customerId");
        try {
            Page<CustomerActivity> page = customerService.getHistory(customerId, PageRequest.of(0, MAX_RESULTS));
            List<CustomerActivityToolResult> activities = page.getContent().stream()
                    .map(a -> new CustomerActivityToolResult(a.getType().name(), a.getContent(), a.getCreatedAt()))
                    .toList();
            logOutcome("getCustomerHistory", start, activities.size());
            return CustomerHistoryResult.of(activities);
        } catch (CustomerNotFoundException e) {
            logOutcome("getCustomerHistory", start, 0);
            return CustomerHistoryResult.notFound();
        }
    }

    private static void requireNonNull(UUID id, String paramName) {
        if (id == null) {
            throw new IllegalArgumentException(paramName + " must be a valid UUID.");
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
        // Metadata only (project instructions §25) — never the query text,
        // never customer data.
        log.info("AI tool call succeeded [tool={}, resultCount={}, durationMs={}]", toolName, resultCount,
                durationMs);
    }

    private static CustomerToolResult toResult(Customer customer) {
        return new CustomerToolResult(
                customer.getId(),
                customer.getName(),
                customer.getCompany(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getGstin(),
                customer.getStatus(),
                customer.getNotes()
        );
    }
}
