package com.bizpilot.documents.extraction;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * DOCX text extraction (Phase 15, CLAUDE.md §16/§17) via Apache POI's
 * {@code XWPFWordExtractor} — paragraph/run text only (headers, footers,
 * tables, and any other structural content POI's own extractor includes).
 * No spreadsheet parsing, no OCR, no image extraction.
 */
@Component
public class DocxTextExtractor implements DocumentTextExtractor {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public boolean supports(String contentType) {
        return DOCX_CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(Resource resource) {
        try (InputStream in = resource.getInputStream();
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read the stored DOCX file", e);
        } catch (RuntimeException e) {
            // POI throws several unchecked types for structurally invalid
            // OOXML content (e.g. a non-Office ZIP, a corrupt part) —
            // org.apache.poi.POIXMLException among them. All are treated
            // uniformly as a processing failure.
            throw new DocumentProcessingException("Failed to extract text from DOCX content", e);
        }
    }
}
