package com.bizpilot.products.exception;

/**
 * Thrown when creating/updating a product would violate the
 * organization-scoped SKU uniqueness rule (docs/database.md) — never a
 * global uniqueness rule, since the same SKU may legitimately belong to
 * products of two different organizations.
 */
public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with SKU '" + sku + "' already exists in this organization");
    }
}
