package com.bizpilot.tasks.exception;

import java.util.UUID;

/**
 * Thrown when a task references a lead that does not exist, or exists but
 * belongs to a different organization, or is archived — a lead from
 * Organization B must never be attachable to a task in Organization A, and
 * an archived lead is not a valid target for a new/updated reference,
 * mirroring the reasoning in
 * {@code sales.exception.InvalidCustomerReferenceException} (reused as-is
 * for Task's customer reference; this is the lead-side equivalent, since no
 * generalized lead-reference exception previously existed).
 */
public class InvalidLeadReferenceException extends RuntimeException {

    public InvalidLeadReferenceException(UUID leadId) {
        super("No such lead in this organization: " + leadId);
    }

    public InvalidLeadReferenceException(UUID leadId, String reason) {
        super("Lead " + leadId + " cannot be used: " + reason);
    }
}
