package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractionServiceTest {

    @Test
    void dispatchesToTheExtractorThatSupportsTheContentType() {
        DocumentTextExtractor pdf = fixedExtractor("application/pdf", "pdf text");
        DocumentTextExtractor txt = fixedExtractor("text/plain", "txt text");
        TextExtractionService service = new TextExtractionService(List.of(pdf, txt));

        assertThat(service.extract("text/plain", null)).isEqualTo("txt text");
        assertThat(service.extract("application/pdf", null)).isEqualTo("pdf text");
    }

    @Test
    void throwsForAnUnsupportedContentTypeRatherThanFallingBackToAnyExtractor() {
        TextExtractionService service = new TextExtractionService(
                List.of(fixedExtractor("application/pdf", "pdf text")));

        assertThatThrownBy(() -> service.extract("application/octet-stream", null))
                .isInstanceOf(DocumentProcessingException.class);
    }

    private static DocumentTextExtractor fixedExtractor(String contentType, String result) {
        return new DocumentTextExtractor() {
            @Override
            public boolean supports(String candidateContentType) {
                return contentType.equals(candidateContentType);
            }

            @Override
            public String extract(Resource resource) {
                return result;
            }
        };
    }
}
