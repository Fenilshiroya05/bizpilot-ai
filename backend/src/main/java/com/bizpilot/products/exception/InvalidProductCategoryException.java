package com.bizpilot.products.exception;

import java.util.UUID;

/**
 * Thrown when a product create/update request references a category that
 * does not exist, or exists but belongs to a different organization — a
 * category from Organization B must never be assignable to a product in
 * Organization A (project instructions §3/§12). Distinct from
 * {@link ProductCategoryNotFoundException} (used for direct category CRUD)
 * the same way {@code sales.exception.InvalidAssigneeException} is distinct
 * from a resource-not-found error — this is a referenced-entity validation
 * failure on the product request, not a "get this category" failure.
 */
public class InvalidProductCategoryException extends RuntimeException {

    public InvalidProductCategoryException(UUID categoryId) {
        super("No such product category in this organization: " + categoryId);
    }
}
