package com.bizpilot.products.exception;

import java.util.UUID;

/**
 * Thrown both when a product id truly doesn't exist AND when it belongs to
 * another organization — indistinguishable to the client by design, so a
 * cross-tenant probe never learns whether a given id exists in another
 * tenant (project instructions §12).
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found: " + id);
    }
}
