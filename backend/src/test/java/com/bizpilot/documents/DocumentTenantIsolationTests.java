package com.bizpilot.documents;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 13 tenant-isolation / IDOR tests — mirrors
 * {@code TaskTenantIsolationTests}. An authenticated user from
 * Organization A must never read, download, or delete Organization B's
 * documents, and must never discover them through listing.
 *
 * <p>Documents have no cross-module business-entity reference to validate
 * (CLAUDE.md §16 defines none — see {@code Document}'s Javadoc), so there
 * is no "cross-org customer/lead reference" case here the way Task/
 * Quotation/Invoice have; the tenant-isolation surface is limited to the
 * document resource itself plus storage-key namespacing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DocumentTenantIsolationTests {

    private static final byte[] PDF_BYTES = "%PDF-1.7 minimal valid pdf body".getBytes(StandardCharsets.US_ASCII);

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void organizationACannotReadOrganizationBsDocumentMetadata() {
        String tokenA = managerToken("doc13-isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("doc13-isoB-read@example.com", "Iso Org B Read");
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + documentB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test
    void organizationACannotDownloadOrganizationBsDocument() {
        String tokenA = managerToken("doc13-isoA-download@example.com", "Iso Org A Download");
        String tokenB = managerToken("doc13-isoB-download@example.com", "Iso Org B Download");
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + documentB.id() + "/download", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotDeleteOrganizationBsDocument() {
        String tokenA = managerToken("doc13-isoA-delete@example.com", "Iso Org A Delete");
        String tokenB = managerToken("doc13-isoB-delete@example.com", "Iso Org B Delete");
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/documents/" + documentB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // And organization B's document must genuinely still exist and be
        // downloadable — the failed cross-org attempt must not have
        // touched it at all.
        ResponseEntity<DocumentResponse> stillThere = getWithToken(
                "/api/v1/documents/" + documentB.id(), tokenB, DocumentResponse.class);
        assertThat(stillThere.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void organizationAsDocumentListingNeverIncludesOrganizationBsDocuments() {
        String tokenA = managerToken("doc13-isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("doc13-isoB-listing@example.com", "Iso Org B Listing");
        DocumentResponse documentA = uploadForOwnOrg(tokenA);
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        ResponseEntity<String> response = getWithToken("/api/v1/documents", tokenA, String.class);

        assertThat(response.getBody()).contains(documentA.id().toString());
        assertThat(response.getBody()).doesNotContain(documentB.id().toString());
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheUploadRequest() {
        String tokenA = managerToken("doc13-isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("doc13-isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        // No JSON metadata is accepted alongside the file part at all
        // (project instructions §19), so there is no field to smuggle an
        // organizationId through in the first place — this proves that by
        // uploading as org A and confirming org B can still never see it,
        // regardless of any such attempt.
        ResponseEntity<DocumentResponse> response = upload(tokenA, "file.pdf", "application/pdf", PDF_BYTES,
                DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiError> crossOrgAttempt = getWithToken(
                "/api/v1/documents/" + response.getBody().id(), tokenB, ApiError.class);
        assertThat(crossOrgAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Sanity: org B's own, separately-uploaded document is unaffected.
        ResponseEntity<DocumentResponse> stillThere = getWithToken(
                "/api/v1/documents/" + documentB.id(), tokenB, DocumentResponse.class);
        assertThat(stillThere.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsDocument() {
        String tokenA = managerToken("doc13-isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("doc13-isoB-query@example.com", "Iso Org B Query");
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + documentB.id() + "?organizationId=00000000-0000-0000-0000-000000000000",
                tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsDocument() {
        String tokenA = managerToken("doc13-isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("doc13-isoB-header@example.com", "Iso Org B Header");
        DocumentResponse documentB = uploadForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", "00000000-0000-0000-0000-000000000000");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/documents/" + documentB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private DocumentResponse uploadForOwnOrg(String token) {
        ResponseEntity<DocumentResponse> response = upload(token, "file.pdf", "application/pdf", PDF_BYTES,
                DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

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

    private <T> ResponseEntity<T> getWithToken(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), responseType);
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
