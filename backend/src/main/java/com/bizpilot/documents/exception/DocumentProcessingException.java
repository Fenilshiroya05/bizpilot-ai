package com.bizpilot.documents.exception;

/**
 * Thrown for any failure of the Phase 15 processing pipeline itself:
 * extraction (corrupt/unreadable/empty PDF, TXT, or DOCX), chunking
 * producing no embeddable chunks, or an unsupported content type reaching
 * the extraction dispatcher. Always caught by
 * {@code DocumentProcessingService}, which transitions the document to
 * {@code FAILED} — never surfaced through a REST API (Phase 15 has none),
 * so unlike {@code DocumentStorageException} this is never registered with
 * {@code GlobalExceptionHandler}.
 *
 * <p>The message is always a short, generic description of which pipeline
 * stage failed and why in structural terms (e.g. "extracted text is empty")
 * — never the document's extracted text or any chunk content.
 */
public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String message) {
        super(message);
    }

    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
