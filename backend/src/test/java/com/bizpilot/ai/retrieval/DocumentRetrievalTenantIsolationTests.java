package com.bizpilot.ai.retrieval;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.ai.FakeAiInfrastructureTestConfig;
import com.bizpilot.ai.vectorstore.DocumentVectorStoreService;
import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.documents.entity.DocumentStatus;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.security.UserPrincipal;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The mandatory Phase 15 security tests (project instructions §22/§23/§35/
 * §49): a mandatory tenant filter at vector-query time, fail-closed
 * retrieval with no authenticated tenant context, and a defense-in-depth
 * check that vector deletion's organizationId+documentId filter can never
 * touch another organization's data even if called with a mismatched pair.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakeAiInfrastructureTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bizpilot.ai.enabled=true",
        "spring.ai.vectorstore.type=pgvector"
})
class DocumentRetrievalTenantIsolationTests {

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

    @Autowired
    private DocumentRetrievalService documentRetrievalService;

    @Autowired
    private DocumentVectorStoreService documentVectorStoreService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retrievalNeverReturnsAnotherOrganizationsChunks() {
        String tokenA = managerToken("rag-isoA@example.com", "Rag Iso Org A");
        String tokenB = managerToken("rag-isoB@example.com", "Rag Iso Org B");

        DocumentResponse documentA = upload(tokenA, "org-a-notes.txt",
                "Organization A confidential quarterly revenue figures and customer list.");
        DocumentResponse documentB = upload(tokenB, "org-b-notes.txt",
                "Organization B confidential quarterly revenue figures and customer list.");
        awaitCompleted(tokenA, documentA.id());
        awaitCompleted(tokenB, documentB.id());

        authenticateAs(organizationIdFor("rag-isoA@example.com"));
        List<RetrievedChunk> results = documentRetrievalService.search(
                "confidential quarterly revenue figures and customer list", 20);

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(chunk -> chunk.documentId().equals(documentA.id()));
        assertThat(results).noneMatch(chunk -> chunk.documentId().equals(documentB.id()));
    }

    @Test
    void retrievalFailsClosedWhenNoAuthenticatedTenantContextExists() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> documentRetrievalService.search("anything", 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void vectorDeletionFilterNeverMatchesAMismatchedOrganizationDocumentPair() {
        String tokenA = managerToken("rag-isoA-delete@example.com", "Rag Iso Delete Org A");
        DocumentResponse documentA = upload(tokenA, "org-a-delete-notes.txt",
                "Organization A data that must survive an attempted cross-tenant delete.");
        awaitCompleted(tokenA, documentA.id());

        UUID organizationAId = organizationIdFor("rag-isoA-delete@example.com");
        Integer beforeCount = vectorRowCountFor(documentA.id());
        assertThat(beforeCount).isGreaterThan(0);

        // A forged/mismatched organizationId paired with a real document id
        // from a DIFFERENT organization — the AND filter must match zero rows.
        documentVectorStoreService.deleteForDocument(UUID.randomUUID(), documentA.id());

        Integer afterCount = vectorRowCountFor(documentA.id());
        assertThat(afterCount).isEqualTo(beforeCount);

        // Sanity: the real organization+document pair genuinely can delete it.
        documentVectorStoreService.deleteForDocument(organizationAId, documentA.id());
        assertThat(vectorRowCountFor(documentA.id())).isZero();
    }

    // ---- Helpers -----------------------------------------------------------------

    private Integer vectorRowCountFor(UUID documentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class, documentId.toString());
    }

    private void authenticateAs(UUID organizationId) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), organizationId, Set.of("AI_USE"));
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("AI_USE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private void awaitCompleted(String token, UUID documentId) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            DocumentResponse current = getWithToken("/api/v1/documents/" + documentId, token, DocumentResponse.class)
                    .getBody();
            assertThat(current).isNotNull();
            assertThat(current.status()).isEqualTo(DocumentStatus.COMPLETED);
        });
    }

    private DocumentResponse upload(String token, String filename, String text) {
        Resource fileResource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType("text/plain"));
        HttpEntity<Resource> filePart = new HttpEntity<>(fileResource, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<DocumentResponse> response = restTemplate.postForEntity(
                url("/api/v1/documents"), new HttpEntity<>(body, headers), DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private <T> ResponseEntity<T> getWithToken(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private UUID organizationIdFor(String email) {
        return new TransactionTemplate(transactionManager).execute(status ->
                userRepository.findByEmailIgnoreCase(email).orElseThrow().getOrganization().getId());
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
