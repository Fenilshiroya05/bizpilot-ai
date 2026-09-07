package com.bizpilot.documents.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TxtTextExtractorTest {

    private final TxtTextExtractor extractor = new TxtTextExtractor();

    @Test
    void supportsOnlyTheTxtContentType() {
        assertThat(extractor.supports("text/plain")).isTrue();
        assertThat(extractor.supports("application/pdf")).isFalse();
    }

    @Test
    void extractsUtf8Text() {
        byte[] bytes = "Business plan — Q1 targets: ₹10,00,000".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(new ByteArrayResource(bytes));

        assertThat(text).isEqualTo("Business plan — Q1 targets: ₹10,00,000");
    }

    @Test
    void extractsEmptyTextFromAnEmptyFile() {
        String text = extractor.extract(new ByteArrayResource(new byte[0]));

        assertThat(text).isEmpty();
    }

    @Test
    void extractsWhitespaceOnlyTextUnchanged() {
        byte[] bytes = "   \n\t  ".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(new ByteArrayResource(bytes));

        assertThat(text).isBlank();
    }
}
