package com.bizpilot.products.exception;

import java.util.UUID;

/**
 * Thrown for direct category CRUD operations (get/update/delete by id) when
 * no category matches the current organization — same indistinguishable
 * not-found-vs-cross-tenant design as {@link ProductNotFoundException}.
 */
public class ProductCategoryNotFoundException extends RuntimeException {

    public ProductCategoryNotFoundException(UUID id) {
        super("Product category not found: " + id);
    }
}
