package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown both when an invoice id truly doesn't exist AND when it belongs to
 * another organization — indistinguishable to the client by design, so a
 * cross-tenant probe never learns whether a given id exists in another
 * tenant. Mirrors {@code QuotationNotFoundException}.
 */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(UUID id) {
        super("Invoice not found: " + id);
    }
}
