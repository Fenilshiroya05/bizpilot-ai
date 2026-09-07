package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void supportsOnlyThePdfContentType() {
        assertThat(extractor.supports("application/pdf")).isTrue();
        assertThat(extractor.supports("text/plain")).isFalse();
        assertThat(extractor.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isFalse();
    }

    @Test
    void extractsTextFromAValidPdf() throws IOException {
        byte[] pdfBytes = buildPdfWithText("Hello from a real BizPilot AI PDF.");

        String text = extractor.extract(new ByteArrayResource(pdfBytes));

        assertThat(text).contains("Hello from a real BizPilot AI PDF.");
    }

    @Test
    void extractsWhitespaceOnlyTextFromAnEmptyPdf() throws IOException {
        byte[] pdfBytes = buildPdfWithText("");

        String text = extractor.extract(new ByteArrayResource(pdfBytes));

        assertThat(text).isBlank();
    }

    @Test
    void throwsDocumentProcessingExceptionForACorruptPdf() {
        byte[] corrupt = "%PDF-1.7 this is not a real PDF body at all".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> extractor.extract(new ByteArrayResource(corrupt)))
                .isInstanceOf(DocumentProcessingException.class);
    }

    private static byte[] buildPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (!text.isEmpty()) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
