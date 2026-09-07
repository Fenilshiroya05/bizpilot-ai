package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PDF text extraction (Phase 15, CLAUDE.md §16/§17) via the PDFBox
 * dependency already used for Quotation/Invoice PDF generation (Phase 10) —
 * no second PDF library is introduced. Reads the PDF into memory and
 * extracts text only; the original stored file is never modified.
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    @Override
    public boolean supports(String contentType) {
        return PDF_CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(Resource resource) {
        byte[] bytes;
        try {
            bytes = resource.getContentAsByteArray();
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read the stored PDF file", e);
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            // Covers both a corrupt/unreadable PDF and PDFBox's own
            // InvalidPasswordException (an IOException subtype) for an
            // encrypted PDF — neither is distinguished further; both are a
            // processing failure from this pipeline's point of view.
            throw new DocumentProcessingException("Failed to extract text from PDF content", e);
        }
    }
}
