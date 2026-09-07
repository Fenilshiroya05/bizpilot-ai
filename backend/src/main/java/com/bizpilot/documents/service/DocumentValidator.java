package com.bizpilot.documents.service;

import com.bizpilot.documents.exception.InvalidDocumentException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, dependency-free upload validation (CLAUDE.md §16) — a
 * pure, stateless class with no Spring/persistence dependency, mirroring
 * {@code sales.service.QuotationCalculator}'s architecture, so it stays
 * trivially unit-testable in isolation from the service/storage layer.
 *
 * <p><b>Exactly three supported formats</b> (CLAUDE.md §16: "Supported
 * initial formats: PDF, TXT, DOCX") — extension and declared
 * {@code Content-Type} must agree, and a lightweight magic-byte check
 * catches the case where both lie (e.g. an executable renamed to
 * {@code .pdf} with a spoofed {@code Content-Type} header). No heavyweight
 * content-sniffing dependency (e.g. Apache Tika) is used — three fixed,
 * simple signatures don't justify one.
 */
public final class DocumentValidator {

    /** Kept in sync with {@code spring.servlet.multipart.max-file-size} (application.yml). */
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private static final int MAX_FILENAME_LENGTH = 255;

    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04}; // "PK" — DOCX is an OOXML ZIP container

    private DocumentValidator() {
        // static utility class
    }

    /**
     * Reduces an arbitrary, untrusted original filename to a safe metadata
     * value: only the final path segment is kept (defeats {@code ../},
     * {@code ..\}, and absolute paths on both Unix and Windows in one
     * step — the filename is never used as a path itself, but this keeps
     * the *displayed* value sane too), then every ISO control character
     * (including null bytes, {@code CR}, {@code LF} — relevant for HTTP
     * header injection via {@code Content-Disposition}) is stripped, and
     * the result is length-capped. All other Unicode — including non-Latin
     * scripts — is preserved untouched.
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unnamed";
        }
        String basename = originalFilename;
        int lastSeparator = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            basename = basename.substring(lastSeparator + 1);
        }

        StringBuilder sanitized = new StringBuilder(basename.length());
        for (int i = 0; i < basename.length(); i++) {
            char c = basename.charAt(i);
            if (!Character.isISOControl(c)) {
                sanitized.append(c);
            }
        }

        String result = sanitized.toString().trim();
        if (result.isEmpty()) {
            result = "unnamed";
        }
        if (result.length() > MAX_FILENAME_LENGTH) {
            result = result.substring(0, MAX_FILENAME_LENGTH);
        }
        return result;
    }

    public static void validateSize(long size) {
        if (size <= 0) {
            throw new InvalidDocumentException("File is empty");
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw new InvalidDocumentException("File exceeds the maximum allowed size of 20 MB");
        }
    }

    /**
     * Validates that the (already-sanitized) filename's extension, the
     * declared {@code Content-Type}, and a magic-byte probe of the file's
     * leading bytes are all mutually consistent, then returns the
     * canonical content type string to persist (derived from the
     * extension, not the client's raw header value — normalizes away any
     * casing/whitespace variation a client might send).
     */
    public static String validateAndResolveContentType(String sanitizedFilename, String declaredContentType,
                                                          byte[] headerBytes) {
        String extension = extractExtension(sanitizedFilename);
        String expectedContentType = EXTENSION_TO_CONTENT_TYPE.get(extension);
        if (expectedContentType == null) {
            throw new InvalidDocumentException("Unsupported file extension: ." + extension);
        }
        if (declaredContentType == null || !expectedContentType.equalsIgnoreCase(declaredContentType.trim())) {
            throw new InvalidDocumentException("Declared content type does not match the file extension");
        }
        validateSignature(expectedContentType, headerBytes);
        return expectedContentType;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateSignature(String contentType, byte[] headerBytes) {
        switch (contentType) {
            case "application/pdf" -> {
                if (!startsWith(headerBytes, PDF_SIGNATURE)) {
                    throw new InvalidDocumentException("File content does not match the PDF format");
                }
            }
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                if (!startsWith(headerBytes, ZIP_SIGNATURE)) {
                    throw new InvalidDocumentException("File content does not match the DOCX format");
                }
            }
            case "text/plain" -> {
                // TXT has no reliable magic-byte signature (CLAUDE.md §16) —
                // extension + declared Content-Type agreement (already
                // checked above) is the only practical check. The content
                // is only ever stored and later returned as opaque bytes,
                // never executed or interpreted, so this is not a gap.
            }
            default -> throw new InvalidDocumentException("Unsupported content type: " + contentType);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
