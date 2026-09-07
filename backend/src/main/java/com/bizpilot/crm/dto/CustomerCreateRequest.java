package com.bizpilot.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Fields mirror CLAUDE.md §10 exactly. {@code status} and
 * {@code organizationId} are deliberately absent: status always starts at
 * {@code ACTIVE} (see {@code CustomerService}), and the organization is
 * always derived from the authenticated tenant context, never accepted from
 * the client (project instructions §12).
 *
 * <p>Only {@code name} is required — every other field is optional, per
 * "do not unnecessarily collect personal information." Phone has no format
 * pattern: international formats vary too widely to validate usefully
 * without rejecting legitimate numbers.
 */
public record CustomerCreateRequest(
        @NotBlank @Size(max = 255) String name,

        @Size(max = 255) String company,

        @Email @Size(max = 255) String email,

        @Size(max = 30) String phone,

        @Size(max = 500) String address,

        // Indian GSTIN format: 2-digit state code, 10-char PAN, 1 entity code,
        // fixed 'Z', 1 checksum char. Validated only when supplied; "^$|..."
        // also accepts an empty string (treated the same as omitting it), for
        // consistency with CustomerUpdateRequest's clear-via-empty-string PATCH
        // convention.
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
                message = "GSTIN must be a valid 15-character GSTIN")
        @Size(max = 15)
        String gstin,

        @Size(max = 2000) String notes
) {
}
