package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown when an update/note/assignment is attempted against an
 * already-archived lead — archived leads cannot accidentally be modified
 * through normal endpoints (project instructions §12).
 */
public class LeadArchivedException extends RuntimeException {

    public LeadArchivedException(UUID id) {
        super("Lead is archived and cannot be modified: " + id);
    }
}
