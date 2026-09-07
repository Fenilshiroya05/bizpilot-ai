package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches to the {@link DocumentTextExtractor} whose {@link
 * DocumentTextExtractor#supports(String)} matches the document's own,
 * already-validated {@code contentType} (project instructions §19) —
 * exactly one of the three fixed formats, never a permissive fallback.
 */
@Component
public class TextExtractionService {

    private final List<DocumentTextExtractor> extractors;

    public TextExtractionService(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public String extract(String contentType, Resource resource) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new DocumentProcessingException("Unsupported content type for extraction: " + contentType))
                .extract(resource);
    }
}
