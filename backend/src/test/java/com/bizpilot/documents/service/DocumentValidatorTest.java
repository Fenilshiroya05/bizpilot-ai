package com.bizpilot.documents.service;

import com.bizpilot.documents.exception.InvalidDocumentException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage of {@link DocumentValidator} — pure functions, no
 * Spring/persistence dependency, mirroring
 * {@code sales.service.QuotationCalculatorTest}'s structure.
 */
class DocumentValidatorTest {

    private static final byte[] PDF_HEADER = "%PDF-1.7 rest of file".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_HEADER = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
    private static final byte[] EXE_HEADER = {0x4D, 0x5A, 0x00, 0x00}; // "MZ" — Windows PE header

    // ---- validateSize -------------------------------------------------------------

    @Test
    void validateSizeAcceptsAPositiveSizeWithinTheLimit() {
        DocumentValidator.validateSize(1024);
    }

    @Test
    void validateSizeRejectsZero() {
        assertThatThrownBy(() -> DocumentValidator.validateSize(0)).isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateSizeRejectsNegative() {
        assertThatThrownBy(() -> DocumentValidator.validateSize(-1)).isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateSizeRejectsOverTwentyMegabytes() {
        assertThatThrownBy(() -> DocumentValidator.validateSize(DocumentValidator.MAX_FILE_SIZE_BYTES + 1))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateSizeAcceptsExactlyTwentyMegabytes() {
        DocumentValidator.validateSize(DocumentValidator.MAX_FILE_SIZE_BYTES);
    }

    // ---- sanitizeFilename ----------------------------------------------------------

    @Test
    void sanitizeFilenameKeepsAnOrdinarySafeFilenameUnchanged() {
        assertThat(DocumentValidator.sanitizeFilename("contract.pdf")).isEqualTo("contract.pdf");
    }

    @Test
    void sanitizeFilenameStripsUnixPathTraversalToTheFinalSegment() {
        assertThat(DocumentValidator.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
    }

    @Test
    void sanitizeFilenameStripsWindowsPathTraversalToTheFinalSegment() {
        assertThat(DocumentValidator.sanitizeFilename("..\\..\\secret.txt")).isEqualTo("secret.txt");
    }

    @Test
    void sanitizeFilenameStripsAnAbsoluteUnixPathToTheFinalSegment() {
        assertThat(DocumentValidator.sanitizeFilename("/etc/shadow")).isEqualTo("shadow");
    }

    @Test
    void sanitizeFilenameStripsAnAbsoluteWindowsPathToTheFinalSegment() {
        assertThat(DocumentValidator.sanitizeFilename("C:\\secret\\file.pdf")).isEqualTo("file.pdf");
    }

    @Test
    void sanitizeFilenameRemovesNullBytes() {
        String result = DocumentValidator.sanitizeFilename("evil\0name.pdf");
        assertThat(result).isEqualTo("evilname.pdf");
        assertThat(result).doesNotContain("\0");
    }

    @Test
    void sanitizeFilenameRemovesCarriageReturnAndLineFeed() {
        String result = DocumentValidator.sanitizeFilename("name\r\nX-Injected: true.pdf");
        assertThat(result).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void sanitizeFilenamePreservesLegitimateUnicodeCharacters() {
        assertThat(DocumentValidator.sanitizeFilename("भारत-अनुबंध.pdf")).isEqualTo("भारत-अनुबंध.pdf");
    }

    @Test
    void sanitizeFilenameTruncatesExtremelyLongNames() {
        String longName = "a".repeat(500) + ".pdf";
        String result = DocumentValidator.sanitizeFilename(longName);
        assertThat(result.length()).isLessThanOrEqualTo(255);
    }

    @Test
    void sanitizeFilenameFallsBackToUnnamedForNullOrBlank() {
        assertThat(DocumentValidator.sanitizeFilename(null)).isEqualTo("unnamed");
        assertThat(DocumentValidator.sanitizeFilename("   ")).isEqualTo("unnamed");
    }

    // ---- validateAndResolveContentType ---------------------------------------------

    @Test
    void validateAndResolveContentTypeAcceptsAValidPdf() {
        String resolved = DocumentValidator.validateAndResolveContentType("file.pdf", "application/pdf", PDF_HEADER);
        assertThat(resolved).isEqualTo("application/pdf");
    }

    @Test
    void validateAndResolveContentTypeAcceptsAValidTxtWithNoSignatureCheck() {
        String resolved = DocumentValidator.validateAndResolveContentType(
                "notes.txt", "text/plain", "just plain text".getBytes(StandardCharsets.UTF_8));
        assertThat(resolved).isEqualTo("text/plain");
    }

    @Test
    void validateAndResolveContentTypeAcceptsAValidDocx() {
        String resolved = DocumentValidator.validateAndResolveContentType(
                "letter.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP_HEADER);
        assertThat(resolved).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void validateAndResolveContentTypeRejectsAnUnsupportedExtension() {
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType(
                "script.exe", "application/octet-stream", EXE_HEADER))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateAndResolveContentTypeRejectsAMismatchedDeclaredContentTypeForPdfExtension() {
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType(
                "file.pdf", "text/plain", PDF_HEADER))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateAndResolveContentTypeRejectsAMismatchedDeclaredContentTypeForTxtExtension() {
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType(
                "notes.txt", "application/pdf", "plain text".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateAndResolveContentTypeRejectsAMismatchedDeclaredContentTypeForDocxExtension() {
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType(
                "letter.docx", "application/pdf", ZIP_HEADER))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateAndResolveContentTypeRejectsAnExecutableRenamedToPdfDespiteCorrectExtensionAndDeclaredType() {
        // The critical spoofing case: extension says .pdf, declared Content-Type
        // says application/pdf, but the actual bytes are a Windows PE header.
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType(
                "malware.pdf", "application/pdf", EXE_HEADER))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateAndResolveContentTypeRejectsInvalidDocxContentDespiteCorrectExtensionAndDeclaredType() {
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType(
                "fake.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a zip file at all".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void validateAndResolveContentTypeRejectsANullDeclaredContentType() {
        assertThatThrownBy(() -> DocumentValidator.validateAndResolveContentType("file.pdf", null, PDF_HEADER))
                .isInstanceOf(InvalidDocumentException.class);
    }
}
