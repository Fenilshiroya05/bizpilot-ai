package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.springframework.core.io.Resource;

/**
 * Strategy for extracting plain text from one supported content type
 * (Phase 15, CLAUDE.md §16/§17). Implementations are explicitly mapped to
 * exactly one of the three fixed, already-validated content types
 * (CLAUDE.md §16) — there is deliberately no permissive "try every parser"
 * fallback (project instructions §19); {@link TextExtractionService}
 * dispatches strictly by {@code Document.contentType}.
 *
 * <p>Implementations read content only through the {@link Resource}
 * supplied by {@code DocumentStorageService.load} — never by constructing a
 * filesystem path themselves — and never modify or persist a copy of the
 * original file.
 */
public interface DocumentTextExtractor {

    boolean supports(String contentType);

    /**
     * @throws DocumentProcessingException if the content cannot be parsed
     *                                      (corrupt, unreadable, or
     *                                      structurally invalid for this
     *                                      format). Does not itself reject
     *                                      empty/whitespace-only results —
     *                                      that check is centralized in
     *                                      {@code DocumentProcessingService}.
     */
    String extract(Resource resource);
}
