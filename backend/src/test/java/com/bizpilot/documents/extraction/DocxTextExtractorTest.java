package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTextExtractorTest {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void supportsOnlyTheDocxContentType() {
        assertThat(extractor.supports(DOCX_CONTENT_TYPE)).isTrue();
        assertThat(extractor.supports("application/pdf")).isFalse();
    }

    @Test
    void extractsParagraphTextFromAValidDocx() throws IOException {
        byte[] docxBytes = buildDocxWithParagraph("Quarterly business review — confidential.");

        String text = extractor.extract(new ByteArrayResource(docxBytes));

        assertThat(text).contains("Quarterly business review — confidential.");
    }

    @Test
    void extractsBlankTextFromAnEmptyDocx() throws IOException {
        byte[] docxBytes = buildDocxWithParagraph(null);

        String text = extractor.extract(new ByteArrayResource(docxBytes));

        assertThat(text).isBlank();
    }

    @Test
    void throwsDocumentProcessingExceptionForACorruptDocx() {
        byte[] corrupt = "not a real zip/ooxml file at all".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> extractor.extract(new ByteArrayResource(corrupt)))
                .isInstanceOf(DocumentProcessingException.class);
    }

    private static byte[] buildDocxWithParagraph(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            if (text != null) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(text);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
}
