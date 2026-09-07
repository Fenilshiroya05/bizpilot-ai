package com.bizpilot.tasks.dto;

import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update (PATCH semantics): every field is optional and {@code null}
 * means "leave unchanged" — same convention as {@code QuotationUpdateRequest}/
 * {@code LeadUpdateRequest}. {@code clearDueDate}/{@code clearCustomerId}/
 * {@code clearLeadId} mirror {@code LeadUpdateRequest.clearFollowUpDate} —
 * the explicit signal needed to clear an optional field, which a plain
 * {@code null} can't express under this convention. {@code description}/
 * {@code notes} use the established alternative for plain text fields
 * instead (see {@code TaskService}): sending an empty string clears them,
 * since {@code trimToNull} already collapses blank input to {@code null}
 * when applied — the same mechanism {@code LeadService} already relies on
 * for {@code company}/{@code email}/{@code phone}.
 *
 * <p><b>{@code assignedToUserId} is deliberately absent</b> — assignment
 * only happens through the dedicated {@code /assign} endpoint (project
 * instructions §10), to avoid the same "field omitted = no change" vs.
 * "field null = unassign" ambiguity {@code LeadUpdateRequest} avoids for the
 * identical reason.
 *
 * <p>{@code status} accepts every value except {@code CANCELLED} — reaching
 * {@code CANCELLED} is only possible through the dedicated
 * {@code DELETE /api/v1/tasks/{id}} endpoint. Unlike Quotation/Invoice,
 * there is no other status restriction: a task may be freely moved between
 * {@code TODO}/{@code IN_PROGRESS}/{@code COMPLETED} in either direction,
 * including "reopening" a completed task, regardless of its current status
 * (project instructions §7/§8 — Tasks are not immutable at any status).
 */
public record TaskUpdateRequest(
        @Size(max = 255) String title,

        @Size(max = 2000) String description,

        TaskPriority priority,

        LocalDate dueDate,

        boolean clearDueDate,

        UUID customerId,

        boolean clearCustomerId,

        UUID leadId,

        boolean clearLeadId,

        TaskStatus status,

        @Size(max = 2000) String notes
) {
}
