package com.bizpilot.crm.exception;

/**
 * Thrown when creating/updating a customer would violate the
 * organization-scoped email uniqueness rule (docs/database.md) — never a
 * global uniqueness rule, since the same email may legitimately belong to
 * customers of two different organizations.
 */
public class DuplicateCustomerException extends RuntimeException {

    public DuplicateCustomerException(String email) {
        super("A customer with email '" + email + "' already exists in this organization");
    }
}
