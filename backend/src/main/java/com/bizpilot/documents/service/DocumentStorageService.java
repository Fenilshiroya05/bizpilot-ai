package com.bizpilot.documents.service;

import com.bizpilot.documents.exception.DocumentStorageException;
import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Storage abstraction for document binary content (CLAUDE.md §16: "Create a
 * storage abstraction so local filesystem can be used for development and
 * S3-compatible storage can be used in production"). Business logic
 * ({@code DocumentService}) depends only on this interface, never on
 * {@code java.nio.file.Files} or any concrete storage technology directly —
 * an S3-compatible implementation can be substituted later with no change
 * to the service layer.
 *
 * <p>{@code storageKey} is always a server-generated, opaque identifier
 * (never derived from a client-supplied filename, never accepted from a
 * client at all) — see {@code DocumentService} for how it's constructed.
 *
 * <p>Only a local filesystem implementation ({@code LocalDocumentStorageService})
 * is built in Phase 13 — no S3/MinIO client exists yet; building one now,
 * with no production deployment target decided, would be speculative
 * infrastructure ahead of its assigned need.
 */
public interface DocumentStorageService {

    /**
     * Writes {@code content} to the location identified by {@code storageKey}.
     * Throws {@link DocumentStorageException} on any I/O failure.
     */
    void store(String storageKey, InputStream content);

    /**
     * Resolves {@code storageKey} to a readable {@link Resource}. Throws
     * {@link DocumentStorageException} if the underlying object is missing
     * — a genuine server-side data-integrity problem (the database row
     * claims it should exist), not a client error.
     */
    Resource load(String storageKey);

    /**
     * Deletes the object at {@code storageKey}. <b>Idempotent</b>: deleting
     * an already-missing object is a no-op, not an error — see
     * {@code DocumentService.delete}'s Javadoc for why this is the safest
     * resolution for a storage/database inconsistency in a hard-delete
     * model with no reconciliation job.
     */
    void delete(String storageKey);
}
