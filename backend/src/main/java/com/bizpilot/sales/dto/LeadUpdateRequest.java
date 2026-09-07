package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial update (PATCH semantics): every field is optional and {@code null}
 * means "leave unchanged" — same convention as
 * {@code crm.dto.CustomerUpdateRequest}. Assignment is deliberately excluded
 * here (see the dedicated {@code LeadAssignRequest}/{@code /assign}
 * endpoint) to avoid conflating "no change" with "unassign," which a plain
 * nullable field on this DTO could not distinguish.
 *
 * <p>{@code followUpDate} has the same null-means-unchanged limitation, but a
 * follow-up date specifically needs an explicit way to be *cleared* (not just
 * changed) — {@code clearFollowUpDate} is that explicit signal: when
 * {@code true}, the follow-up date is cleared regardless of
 * {@code followUpDate}'s value. Defaults to {@code false} when omitted (a
 * missing/absent primitive `boolean` property deserializes to `false`).
 */
public record LeadUpdateRequest(
        @Size(max = 255) String name,

        @Size(max = 255) String company,

        @Email @Size(max = 255) String email,

        @Size(max = 30) String phone,

        LeadStatus status,

        LeadSource source,

        LeadPriority priority,

        LocalDate followUpDate,

        boolean clearFollowUpDate
) {
}
