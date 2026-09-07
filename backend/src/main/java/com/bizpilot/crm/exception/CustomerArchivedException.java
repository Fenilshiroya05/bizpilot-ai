package com.bizpilot.crm.exception;

import java.util.UUID;

/**
 * Thrown when an update/note is attempted against an already-archived
 * customer — archived customers cannot accidentally be modified through
 * normal endpoints (project instructions §7).
 */
public class CustomerArchivedException extends RuntimeException {

    public CustomerArchivedException(UUID id) {
        super("Customer is archived and cannot be modified: " + id);
    }
}
