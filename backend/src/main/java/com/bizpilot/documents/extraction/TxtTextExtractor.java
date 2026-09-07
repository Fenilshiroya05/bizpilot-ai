package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * TXT text extraction (Phase 15, CLAUDE.md §16/§17) — read as UTF-8, no
 * library dependency needed.
 */
@Component
public class TxtTextExtractor implements DocumentTextExtractor {

    private static final String TXT_CONTENT_TYPE = "text/plain";

    @Override
    public boolean supports(String contentType) {
        return TXT_CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(Resource resource) {
        try {
            return new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read the stored TXT file", e);
        }
    }
}
