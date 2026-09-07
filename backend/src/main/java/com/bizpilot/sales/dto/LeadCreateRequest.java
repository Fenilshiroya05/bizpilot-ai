package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Deliberately minimal identifying/contact fields (see {@code Lead}'s
 * Javadoc for the full reasoning) — only {@code name} is required.
 * {@code status} and {@code assignedToUserId} are absent: status always
 * starts at {@link com.bizpilot.sales.entity.LeadStatus#NEW}, and assignment
 * only ever happens through the dedicated assign endpoint. {@code source} is
 * required — CLAUDE.md §11 makes lead source a first-class field, and there
 * is no default value that wouldn't misrepresent how the lead was actually
 * acquired. {@code priority} is optional and defaults to
 * {@link LeadPriority#MEDIUM} if omitted.
 */
public record LeadCreateRequest(
        @NotBlank @Size(max = 255) String name,

        @Size(max = 255) String company,

        @Email @Size(max = 255) String email,

        @Size(max = 30) String phone,

        @NotNull LeadSource source,

        LeadPriority priority,

        LocalDate followUpDate
) {
}
