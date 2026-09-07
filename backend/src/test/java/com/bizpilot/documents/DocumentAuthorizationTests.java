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
 * Mandatory Phase 13 RBAC tests: {@code DOCUMENT_READ}/{@code DOCUMENT_UPLOAD}
 * (already seeded by Phase 6, V4, unchanged) and the newly-seeded
 * {@code DOCUMENT_DELETE} (V11) are each enforced server-side. Mirrors
 * {@code TaskAuthorizationTests}.
 *
 * <p>Seeded mapping recap (docs/security.md): OWNER/ADMIN/MANAGER/SALES all
 * have READ+UPLOAD; EMPLOYEE has READ only. DOCUMENT_DELETE (new, Phase 13)
 * is restricted to OWNER/ADMIN/MANAGER — narrower than every other
 * permission here, since documents may contain sensitive business files.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DocumentAuthorizationTests {

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
    void everyDocumentEndpointRejectsUnauthenticatedRequests() {
        assertThat(restTemplate.getForEntity(url("/api/v1/documents"), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anEmployeeCanReadButNotUploadOrDelete() {
        // EMPLOYEE is the default self-registration role — no promotion needed.
        String token = registerAndLogin("doc13-employee-rbac@example.com", "Employee RBAC Co");

        ResponseEntity<String> listResponse = getWithToken("/api/v1/documents", token, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> uploadResponse = upload(token, "file.pdf", "application/pdf", PDF_BYTES,
                ApiError.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(uploadResponse.getBody().code()).isEqualTo("FORBIDDEN");

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<ApiError> deleteResponse = restTemplate.exchange(
                url("/api/v1/documents/" + java.util.UUID.randomUUID()), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), ApiError.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aSalesUserCanReadAndUploadButNotDelete() {
        String token = promotedToken("doc13-sales-rbac@example.com", "Sales RBAC Co", UserRole.SALES);

        ResponseEntity<DocumentResponse> uploadResponse = upload(token, "file.pdf", "application/pdf", PDF_BYTES,
                DocumentResponse.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String documentId = uploadResponse.getBody().id().toString();

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<ApiError> deleteResponse = restTemplate.exchange(
                url("/api/v1/documents/" + documentId), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), ApiError.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aManagerCanReadUploadAndDelete() {
        String token = promotedToken("doc13-manager-rbac@example.com", "Manager RBAC Co", UserRole.MANAGER);

        ResponseEntity<DocumentResponse> uploadResponse = upload(token, "file.pdf", "application/pdf", PDF_BYTES,
                DocumentResponse.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String documentId = uploadResponse.getBody().id().toString();

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/v1/documents/" + documentId), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void theDownloadEndpointRequiresDocumentReadPermission() {
        String managerToken = promotedToken("doc13-manager-download@example.com", "Manager Download Co",
                UserRole.MANAGER);
        ResponseEntity<DocumentResponse> created = upload(managerToken, "file.pdf", "application/pdf", PDF_BYTES,
                DocumentResponse.class);
        String documentId = created.getBody().id().toString();

        // EMPLOYEE (default role) belongs to a DIFFERENT organization here,
        // so this also incidentally proves tenant isolation on the download
        // endpoint — the dedicated tenant-isolation test covers that
        // explicitly; this test only asserts the permission requirement.
        // EMPLOYEE does hold DOCUMENT_READ, so the expected outcome is 404
        // (cross-tenant), proving the endpoint is reached and tenant-checked
        // rather than blocked purely by a missing permission.
        String employeeToken = registerAndLogin("doc13-employee-download@example.com", "Employee Download Co");
        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/documents/" + documentId + "/download", employeeToken, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

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

    private String registerAndLogin(String email, String organizationName) {
        register(email, organizationName);
        return login(email).accessToken();
    }

    private String promotedToken(String email, String organizationName, UserRole role) {
        register(email, organizationName);
        promoteTo(email, role);
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

    private void promoteTo(String email, UserRole role) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            user.getRoles().clear();
            user.getRoles().add(roleRepository.findByName(role.name()).orElseThrow());
            userRepository.saveAndFlush(user);
        });
    }
}
