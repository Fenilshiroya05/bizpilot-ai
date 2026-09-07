package com.bizpilot.documents;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.documents.entity.DocumentStatus;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CRUD/validation/search/download coverage for the Phase 13
 * Document API, against a real PostgreSQL instance and the local
 * filesystem storage implementation. Every test operates as a MANAGER
 * within a single organization — RBAC/tenant-isolation-specific scenarios
 * live in {@link DocumentAuthorizationTests} and {@link DocumentTenantIsolationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DocumentApiTests {

    private static final byte[] PDF_BYTES = "%PDF-1.7 minimal valid pdf body".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_BYTES = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void uploadingAValidPdfReturns201WithMetadata() {
        String token = managerToken("doc13-pdf@example.com", "Pdf Co");

        ResponseEntity<DocumentResponse> response = upload(token, "contract.pdf", "application/pdf", PDF_BYTES,
                DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        DocumentResponse body = response.getBody();
        assertThat(body.originalFilename()).isEqualTo("contract.pdf");
        assertThat(body.contentType()).isEqualTo("application/pdf");
        assertThat(body.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(body.fileSize()).isEqualTo(PDF_BYTES.length);
    }

    @Test
    void uploadingAValidTxtReturns201() {
        String token = managerToken("doc13-txt@example.com", "Txt Co");
        byte[] content = "plain text content".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<DocumentResponse> response = upload(token, "notes.txt", "text/plain", content,
                DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().contentType()).isEqualTo("text/plain");
    }

    @Test
    void uploadingAValidDocxReturns201() {
        String token = managerToken("doc13-docx@example.com", "Docx Co");

        ResponseEntity<DocumentResponse> response = upload(token, "letter.docx", DOCX_CONTENT_TYPE, ZIP_BYTES,
                DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().contentType()).isEqualTo(DOCX_CONTENT_TYPE);
    }

    @Test
    void uploadingAnEmptyFileFailsValidation() {
        String token = managerToken("doc13-empty@example.com", "Empty Co");

        ResponseEntity<ApiError> response = upload(token, "empty.pdf", "application/pdf", new byte[0],
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_DOCUMENT");
    }

    @Test
    void uploadingAnUnsupportedExtensionFailsValidation() {
        String token = managerToken("doc13-exe@example.com", "Exe Co");

        ResponseEntity<ApiError> response = upload(token, "script.exe", "application/octet-stream",
                new byte[]{0x4D, 0x5A}, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_DOCUMENT");
    }

    @Test
    void uploadingWithAMismatchedContentTypeFailsValidation() {
        String token = managerToken("doc13-mismatch@example.com", "Mismatch Co");

        ResponseEntity<ApiError> response = upload(token, "notes.txt", "application/pdf",
                "plain text".getBytes(StandardCharsets.UTF_8), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_DOCUMENT");
    }

    @Test
    void uploadingAMaliciousFilenameSucceedsWithASanitizedName() {
        String token = managerToken("doc13-malicious@example.com", "Malicious Co");

        ResponseEntity<DocumentResponse> response = upload(token, "../../etc/passwd.pdf", "application/pdf",
                PDF_BYTES, DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().originalFilename()).isEqualTo("passwd.pdf");
    }

    @Test
    void metadataResponseNeverExposesStorageKeyOrOrganizationId() {
        String token = managerToken("doc13-safefields@example.com", "SafeFields Co");
        DocumentResponse created = uploadPdf(token, "contract.pdf");

        ResponseEntity<String> raw = getWithToken("/api/v1/documents/" + created.id(), token, String.class);

        assertThat(raw.getBody()).doesNotContain("storageKey").doesNotContain("organizationId");
    }

    @Test
    void gettingANonexistentDocumentReturns404() {
        String token = managerToken("doc13-notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test
    void listingSupportsFilteringByStatusAndContentType() {
        String token = managerToken("doc13-filters@example.com", "Filters Co");
        DocumentResponse pdf = uploadPdf(token, "a.pdf");
        uploadTxt(token, "b.txt");

        JsonNode byContentType = listDocuments(token, "contentType=application/pdf");
        assertThat(byContentType.get("content")).hasSize(1);
        assertThat(byContentType.get("content").get(0).get("id").asText()).isEqualTo(pdf.id().toString());

        JsonNode byStatus = listDocuments(token, "status=UPLOADED");
        assertThat(byStatus.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listingSupportsFreeTextSearchByFilename() {
        String token = managerToken("doc13-search@example.com", "Search Co");
        uploadPdf(token, "quarterly-report.pdf");
        uploadPdf(token, "unrelated.pdf");

        JsonNode results = listDocuments(token, "q=quarterly");
        assertThat(results.get("content")).hasSize(1);
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevel() {
        String token = managerToken("doc13-paginate@example.com", "Paginate Co");
        for (int i = 0; i < 5; i++) {
            uploadPdf(token, "file-" + i + ".pdf");
        }

        JsonNode firstPage = listDocuments(token, "page=0&size=2");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void downloadReturnsTheExactBytesWithCorrectHeaders() {
        String token = managerToken("doc13-download@example.com", "Download Co");
        DocumentResponse created = uploadPdf(token, "contract.pdf");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url("/api/v1/documents/" + created.id() + "/download"), HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("contract.pdf");
        assertThat(response.getBody()).isEqualTo(PDF_BYTES);
    }

    @Test
    void downloadReturns404ForANonexistentDocument() {
        String token = managerToken("doc13-downloadnotfound@example.com", "DownloadNotFound Co");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + UUID.randomUUID() + "/download", token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingRemovesTheDocumentAndIsIdempotentInEffect() {
        String token = managerToken("doc13-delete@example.com", "Delete Co");
        DocumentResponse created = uploadPdf(token, "contract.pdf");

        assertThat(deleteWithToken("/api/v1/documents/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // A hard delete: the document is genuinely gone, so a repeated
        // DELETE (and any further GET) now correctly returns 404 — unlike
        // Task/Quotation/Invoice's soft-cancel, whose row still exists.
        assertThat(deleteWithToken("/api/v1/documents/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getWithToken("/api/v1/documents/" + created.id(), token, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletedDocumentContentIsNoLongerDownloadable() {
        String token = managerToken("doc13-deletedownload@example.com", "DeleteDownload Co");
        DocumentResponse created = uploadPdf(token, "contract.pdf");
        deleteWithToken("/api/v1/documents/" + created.id(), token);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + created.id() + "/download", token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private DocumentResponse uploadPdf(String token, String filename) {
        ResponseEntity<DocumentResponse> response = upload(token, filename, "application/pdf", PDF_BYTES,
                DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private DocumentResponse uploadTxt(String token, String filename) {
        ResponseEntity<DocumentResponse> response = upload(token, filename, "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8), DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /**
     * Builds and posts a {@code multipart/form-data} request with a single
     * {@code file} part — no existing precedent in this test suite (Phase
     * 13 is the first to deal with file upload), so this establishes the
     * pattern: each part is wrapped in its own {@link HttpEntity} so its
     * {@code Content-Type} can be set explicitly and independently of the
     * outer request's {@code multipart/form-data} content type — necessary
     * since several tests deliberately send a mismatched per-part content
     * type.
     */
    private <T> ResponseEntity<T> upload(String token, String filename, String contentType, byte[] content,
                                          Class<T> responseType) {
        Resource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<Resource> filePart = new HttpEntity<>(fileResource, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.postForEntity(url("/api/v1/documents"), new HttpEntity<>(body, headers), responseType);
    }

    private JsonNode listDocuments(String token, String queryString) {
        ResponseEntity<String> response = getWithToken("/api/v1/documents?" + queryString, token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> ResponseEntity<T> getWithToken(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<Void> deleteWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }

    private String managerToken(String email, String organizationName) {
        register(email, organizationName);
        promoteToManager(email);
        return login(email).accessToken();
    }

    private void register(String email, String organizationName) {
        restTemplate.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName), Void.class);
    }

    private AuthResponse login(String email) {
        return restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
    }

    private void promoteToManager(String email) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            user.getRoles().clear();
            user.getRoles().add(roleRepository.findByName(UserRole.MANAGER.name()).orElseThrow());
            userRepository.saveAndFlush(user);
        });
    }
}
