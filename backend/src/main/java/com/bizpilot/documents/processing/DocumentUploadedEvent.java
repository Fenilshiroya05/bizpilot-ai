package com.bizpilot.documents.processing;

import java.util.UUID;

/**
 * Published by {@code DocumentService.upload} after the upload transaction
 * commits (project instructions §12/§13). Carries only the document id,
 * never the entity itself — the async listener reloads it fresh, since it
 * runs on a different thread with no access to the request's persistence
 * context.
 */
public record DocumentUploadedEvent(UUID documentId) {
}
