package com.bizpilot.crm.exception;

import java.util.UUID;

/**
 * Thrown both when a customer id truly doesn't exist AND when it belongs to
 * another organization — the two cases are indistinguishable to the client
 * by design, so a cross-tenant probe never learns whether a given id exists
 * in another tenant (project instructions §14).
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(UUID id) {
        super("Customer not found: " + id);
    }
}
