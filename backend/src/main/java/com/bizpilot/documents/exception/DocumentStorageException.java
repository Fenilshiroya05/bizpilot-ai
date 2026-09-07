package com.bizpilot.documents.exception;

/**
 * Thrown for any failure of the storage backend itself: unable to write,
 * read, or delete a stored file, or a stored file unexpectedly missing on
 * read. Always a {@code 500} from the client's point of view — this
 * represents a server-side/infrastructure problem, not a client mistake.
 *
 * <p>The message may reference an internal storage key (never a raw
 * filesystem path, and never the original filename) purely for server-side
 * log correlation — {@code GlobalExceptionHandler} never passes this
 * exception's message through to the client, precisely so that detail is
 * never exposed even though it isn't especially sensitive on its own.
 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message) {
        super(message);
    }

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
