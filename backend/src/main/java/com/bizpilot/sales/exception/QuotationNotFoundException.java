package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown both when a quotation id truly doesn't exist AND when it belongs to
 * another organization — indistinguishable to the client by design, so a
 * cross-tenant probe never learns whether a given id exists in another
 * tenant (project instructions §17).
 */
public class QuotationNotFoundException extends RuntimeException {

    public QuotationNotFoundException(UUID id) {
        super("Quotation not found: " + id);
    }
}
