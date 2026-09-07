package com.bizpilot.crm.dto;

import com.bizpilot.crm.entity.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update (PATCH semantics): every field is optional and {@code null}
 * means "leave unchanged" — see {@code CustomerService#update}. There is no
 * separate PUT/full-update endpoint; one PATCH endpoint covers both partial
 * and full updates, avoiding two near-identical endpoints for a field list
 * this small.
 *
 * <p>{@code status} only accepts {@link CustomerStatus#ACTIVE} or
 * {@link CustomerStatus#INACTIVE} — transitioning to {@code ARCHIVED} is only
 * possible through the dedicated archive endpoint (DELETE), which also
 * records the archival activity; this keeps that transition deliberate and
 * consistent rather than a side effect of a general field update. Enforced
 * in {@code CustomerService}, not by Bean Validation, since Jakarta
 * Validation has no clean way to express "any enum value except one."
 */
public record CustomerUpdateRequest(
        @Size(max = 255) String name,

        @Size(max = 255) String company,

        @Email @Size(max = 255) String email,

        @Size(max = 30) String phone,

        @Size(max = 500) String address,

        // "^$|..." also accepts an empty string, not just null: since null means
        // "leave unchanged" for a PATCH field, an empty string is the only way a
        // client can clear a previously-set GSTIN (CustomerService normalizes ""
        // to null before persisting) — without this, @Pattern would reject "" and
        // a GSTIN could never be removed once set.
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
                message = "GSTIN must be a valid 15-character GSTIN")
        @Size(max = 15)
        String gstin,

        @Size(max = 2000) String notes,

        CustomerStatus status
) {
}
