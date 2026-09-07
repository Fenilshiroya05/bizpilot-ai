package com.bizpilot.documents.exception;

import java.util.UUID;

/**
 * Thrown both when a document id truly doesn't exist AND when it belongs to
 * another organization — indistinguishable to the client by design, so a
 * cross-tenant probe never learns whether a given id exists in another
 * tenant. Mirrors {@code sales.exception.TaskNotFoundException}.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document not found: " + id);
    }
}
