package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown both when a lead id truly doesn't exist AND when it belongs to
 * another organization — indistinguishable to the client by design, so a
 * cross-tenant probe never learns whether a given id exists in another
 * tenant (project instructions §16/§18).
 */
public class LeadNotFoundException extends RuntimeException {

    public LeadNotFoundException(UUID id) {
        super("Lead not found: " + id);
    }
}
