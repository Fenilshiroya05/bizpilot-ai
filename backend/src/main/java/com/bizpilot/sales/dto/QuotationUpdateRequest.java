package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.QuotationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Partial update (PATCH semantics): every field is optional and {@code null}
 * means "leave unchanged" — same convention as {@code LeadUpdateRequest}/
 * {@code ProductUpdateRequest}. {@code clearValidUntil} mirrors
 * {@code LeadUpdateRequest.clearFollowUpDate} — the explicit signal needed to
 * clear an optional date field, which a plain {@code null} can't express
 * under this convention.
 *
 * <p>{@code items}, when supplied, atomically <b>replaces</b> the entire line
 * item collection (never a partial add/remove/modify of individual items) —
 * the simplest transactional design satisfying "creating/updating a
 * quotation manages its items transactionally" (project instructions §14)
 * without inventing per-item PATCH semantics nothing in this phase asks for.
 * Must be non-empty if supplied (validated in {@code QuotationService}, since
 * Bean Validation's {@code @NotEmpty} can't express "non-empty only when
 * present").
 *
 * <p>{@code status} accepts every value except {@code CANCELLED} — reaching
 * {@code CANCELLED} is only possible through the dedicated
 * {@code DELETE /api/v1/quotations/{id}} endpoint, mirroring
 * {@code CustomerUpdateRequest}'s equivalent restriction on {@code ARCHIVED}.
 */
public record QuotationUpdateRequest(
        UUID customerId,

        LocalDate validUntil,

        boolean clearValidUntil,

        @DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "100", inclusive = true)
        BigDecimal discountPercentage,

        QuotationStatus status,

        @Valid List<QuotationItemRequest> items
) {
}
