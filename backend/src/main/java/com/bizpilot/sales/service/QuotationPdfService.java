package com.bizpilot.sales.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.sales.entity.Quotation;
import com.bizpilot.sales.entity.QuotationItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Renders a quotation as a PDF (CLAUDE.md §14: "Generate professional
 * quotation PDFs" — an unconditional requirement, project instructions §19).
 * Generated on demand and never persisted — no storage abstraction exists
 * until the Documents module (Phase 13), and building one ahead of its
 * assigned phase would be speculative infrastructure this phase must not
 * introduce.
 *
 * <p>Renders exclusively from the already-backend-calculated {@link Quotation}
 * (never from request parameters) — there is no code path here that accepts
 * a financial value from the caller.
 */
@Service
public class QuotationPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;

    private final QuotationService quotationService;

    public QuotationPdfService(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    /**
     * {@code readOnly = true}: joins the same transaction as the underlying
     * {@code QuotationService.getById} call, keeping the lazy {@code customer}
     * association (and {@code items}, already eagerly fetched by that method)
     * safely readable for the whole duration of PDF rendering.
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID quotationId) {
        Quotation quotation = quotationService.getById(quotationId);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                y = writeHeading(content, boldFont, regularFont, quotation, y);
                y = writeCustomerSection(content, boldFont, regularFont, quotation.getCustomer(), y);
                y = writeItemsTable(content, boldFont, regularFont, quotation.getItems(), y);
                writeTotalsSection(content, boldFont, regularFont, quotation, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            // PDFBox's checked IOException here always indicates an unexpected
            // internal rendering failure (never bad user input, already
            // validated well before this point) — no sensitive detail is in
            // the message, but it is logged server-side by the generic
            // GlobalExceptionHandler catch-all, not exposed to the client.
            throw new UncheckedIOException("Failed to generate quotation PDF", e);
        }
    }

    private float writeHeading(PDPageContentStream content, PDType1Font boldFont, PDType1Font regularFont,
                                Quotation quotation, float y) throws IOException {
        y = writeLine(content, boldFont, 18, MARGIN, y, "BizPilot AI — Quotation");
        y -= 6;
        y = writeLine(content, regularFont, 10, MARGIN, y, "Quotation ID: " + quotation.getId());
        y = writeLine(content, regularFont, 10, MARGIN, y,
                "Created: " + DATE_FORMAT.format(quotation.getCreatedAt().atZone(ZoneOffset.UTC)));
        y = writeLine(content, regularFont, 10, MARGIN, y,
                "Valid until: " + (quotation.getValidUntil() != null ? DATE_FORMAT.format(quotation.getValidUntil()) : "N/A"));
        y = writeLine(content, regularFont, 10, MARGIN, y, "Status: " + quotation.getStatus());
        return y - 10;
    }

    private float writeCustomerSection(PDPageContentStream content, PDType1Font boldFont, PDType1Font regularFont,
                                        Customer customer, float y) throws IOException {
        y = writeLine(content, boldFont, 12, MARGIN, y, "Customer");
        y = writeLine(content, regularFont, 10, MARGIN, y, customer.getName());
        if (customer.getCompany() != null) {
            y = writeLine(content, regularFont, 10, MARGIN, y, customer.getCompany());
        }
        if (customer.getEmail() != null) {
            y = writeLine(content, regularFont, 10, MARGIN, y, customer.getEmail());
        }
        return y - 10;
    }

    private float writeItemsTable(PDPageContentStream content, PDType1Font boldFont, PDType1Font regularFont,
                                   List<QuotationItem> items, float y) throws IOException {
        float[] columnX = {MARGIN, MARGIN + 200, MARGIN + 270, MARGIN + 340, MARGIN + 400, MARGIN + 470};
        writeRow(content, boldFont, 9, columnX, y,
                "Product", "Quantity", "Unit Price", "Tax %", "Line Subtotal");
        y -= LINE_HEIGHT;

        for (QuotationItem item : items) {
            writeRow(content, regularFont, 9, columnX, y,
                    item.getProductNameSnapshot(),
                    plain(item.getQuantity()),
                    money(item.getUnitPrice()),
                    plain(item.getTaxPercentage()),
                    money(item.getLineSubtotal()));
            y -= LINE_HEIGHT;
        }
        return y - 10;
    }

    private void writeTotalsSection(PDPageContentStream content, PDType1Font boldFont, PDType1Font regularFont,
                                     Quotation quotation, float y) throws IOException {
        float labelX = MARGIN + 300;
        float valueX = MARGIN + 420;
        y = writeTwoColumn(content, regularFont, labelX, valueX, y, "Subtotal", money(quotation.getSubtotal()));
        y = writeTwoColumn(content, regularFont, labelX, valueX, y,
                "Discount (" + plain(quotation.getDiscountPercentage()) + "%)", money(quotation.getDiscountAmount()));
        y = writeTwoColumn(content, regularFont, labelX, valueX, y, "Tax", money(quotation.getTaxAmount()));
        writeTwoColumn(content, boldFont, labelX, valueX, y, "Grand Total", money(quotation.getGrandTotal()));
    }

    private float writeLine(PDPageContentStream content, PDType1Font font, float fontSize, float x, float y,
                             String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(sanitizeForPdf(font, text));
        content.endText();
        return y - LINE_HEIGHT;
    }

    private void writeRow(PDPageContentStream content, PDType1Font font, float fontSize, float[] columnX, float y,
                           String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(columnX[i], y);
            content.showText(sanitizeForPdf(font, values[i] != null ? values[i] : ""));
            content.endText();
        }
    }

    private float writeTwoColumn(PDPageContentStream content, PDType1Font font, float labelX, float valueX, float y,
                                  String label, String value) throws IOException {
        content.beginText();
        content.setFont(font, 10);
        content.newLineAtOffset(labelX, y);
        content.showText(sanitizeForPdf(font, label));
        content.endText();
        content.beginText();
        content.setFont(font, 10);
        content.newLineAtOffset(valueX, y);
        content.showText(sanitizeForPdf(font, value));
        content.endText();
        return y - LINE_HEIGHT;
    }

    /**
     * Standard 14 PDF fonts (Helvetica/Helvetica-Bold here) only support
     * WinAnsiEncoding — {@code PDType1Font.encode()} throws
     * {@link IllegalArgumentException} (not {@link IOException}) for any
     * character outside it, which would otherwise crash PDF generation for
     * business data BizPilot doesn't control (customer names, product
     * names). Rather than embedding a Unicode TTF font — extra complexity
     * and an asset dependency not justified for this phase — unsupported
     * code points are replaced with {@code '?'}, tested one code point at a
     * time via the non-mutating {@code getStringWidth} probe so surrogate
     * pairs (e.g. emoji) are replaced as a single unit, not split.
     */
    private static String sanitizeForPdf(PDType1Font font, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String glyph = text.substring(i, i + charCount);
            try {
                font.getStringWidth(glyph);
                sanitized.append(glyph);
            } catch (IOException | IllegalArgumentException e) {
                sanitized.append('?');
            }
            i += charCount;
        }
        return sanitized.toString();
    }

    /** Display-only rounding to 2 decimal places — the persisted scale remains {@link QuotationCalculator#MONEY_SCALE}. */
    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
