package com.bizpilot.documents.exception;

/**
 * Thrown for any client-caused upload validation failure: empty file,
 * oversized file, unsupported/mismatched extension or content type, a
 * failed magic-byte signature check, or an unreadable multipart payload.
 * Always a {@code 400} — the request itself is at fault, not server state.
 * Messages on this exception are always static, hand-authored strings
 * (never echoing raw file bytes or paths), so they are safe to pass through
 * to the client via {@code ex.getMessage()}.
 */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }

    public InvalidDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
