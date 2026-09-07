package com.bizpilot.tasks.dto;

import com.bizpilot.tasks.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code status} and {@code organizationId} are absent: status always
 * starts at {@link com.bizpilot.tasks.entity.TaskStatus#TODO} (mirroring
 * {@code QuotationCreateRequest}/{@code InvoiceCreateRequest}, which
 * likewise never accept a starting status), and the organization is always
 * derived from the authenticated tenant context. {@code assignedToUserId}
 * is also absent — assignment only ever happens through the dedicated
 * {@code /assign} endpoint (project instructions §10), even at creation
 * time; a newly created task always starts unassigned.
 *
 * <p>{@code priority} is optional and defaults to {@link TaskPriority#MEDIUM}
 * if omitted.
 */
public record TaskCreateRequest(
        @NotBlank @Size(max = 255) String title,

        @Size(max = 2000) String description,

        TaskPriority priority,

        LocalDate dueDate,

        UUID customerId,

        UUID leadId
) {
}
