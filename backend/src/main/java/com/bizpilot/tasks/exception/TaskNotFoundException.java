package com.bizpilot.tasks.exception;

import java.util.UUID;

/**
 * Thrown both when a task id truly doesn't exist AND when it belongs to
 * another organization — indistinguishable to the client by design, so a
 * cross-tenant probe never learns whether a given id exists in another
 * tenant. Mirrors {@code sales.exception.QuotationNotFoundException}.
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID id) {
        super("Task not found: " + id);
    }
}
