package com.bizpilot.documents.processing;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.ai.FakeAiInfrastructureTestConfig;
import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.documents.entity.DocumentChunk;
import com.bizpilot.documents.entity.DocumentStatus;
import com.bizpilot.documents.repository.DocumentChunkRepository;
import com.bizpilot.documents.repository.DocumentRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end Phase 15 pipeline tests: real upload (over HTTP, exactly like
 * a client would), the real {@code AFTER_COMMIT} event, the real bounded
 * async executor, real extraction/chunking, a fake (deterministic,
 * zero-network — project instructions §44) embedding model, and a real
 * PGVector-backed Postgres (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakeAiInfrastructureTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bizpilot.ai.enabled=true",
        "spring.ai.vectorstore.type=pgvector"
})
class DocumentProcessingIntegrationTests {

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
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void uploadedDocumentIsProcessedAsynchronouslyIntoChunksAndVectors() {
        String token = managerToken("rag-pipeline@example.com", "Rag Pipeline Org");
        String text = "BizPilot AI helps small businesses track customers, leads, quotations, and invoices "
                + "in one place, with an AI assistant that can search the business's own documents.";
        DocumentResponse uploaded = upload(token, "notes.txt", "text/plain",
                text.getBytes(StandardCharsets.UTF_8));

        DocumentResponse completed = awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);
        assertThat(completed.status()).isEqualTo(DocumentStatus.COMPLETED);

        UUID organizationId = organizationIdFor("rag-pipeline@example.com");
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentAndOrganization(uploaded.id(), organizationId);
        assertThat(chunks).isNotEmpty();
        List<Integer> indexes = chunks.stream().map(DocumentChunk::getChunkIndex).sorted().toList();
        assertThat(indexes).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());

        Integer vectorRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class, uploaded.id().toString());
        assertThat(vectorRowCount).isEqualTo(chunks.size());

        // The join key: every document_chunks id has a matching vector_store row.
        for (DocumentChunk chunk : chunks) {
            Integer matching = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vector_store WHERE id = ?", Integer.class, chunk.getId());
            assertThat(matching).isEqualTo(1);
        }
    }

    @Test
    void reprocessingReplacesChunksAndVectorsWithoutDuplicatesOrDuplicateIndexes() {
        String token = managerToken("rag-reprocess@example.com", "Rag Reprocess Org");
        DocumentResponse uploaded = upload(token, "notes.txt", "text/plain",
                "Reprocessing must be idempotent and replace prior chunks and vectors.".getBytes(StandardCharsets.UTF_8));
        awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);

        UUID organizationId = organizationIdFor("rag-reprocess@example.com");
        List<DocumentChunk> firstAttemptChunks = documentChunkRepository.findByDocumentAndOrganization(uploaded.id(), organizationId);
        assertThat(firstAttemptChunks).isNotEmpty();

        // Force FAILED (simulating a prior failed attempt) and reprocess —
        // process() is @Async only through the real Spring proxy, which
        // documentProcessingService (an @Autowired bean) is.
        forceStatus(uploaded.id(), DocumentStatus.FAILED);
        documentProcessingService.process(uploaded.id());
        awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);

        List<DocumentChunk> secondAttemptChunks = documentChunkRepository.findByDocumentAndOrganization(uploaded.id(), organizationId);
        assertThat(secondAttemptChunks).hasSize(firstAttemptChunks.size());
        List<Integer> indexes = secondAttemptChunks.stream().map(DocumentChunk::getChunkIndex).sorted().toList();
        assertThat(indexes).doesNotHaveDuplicates();

        Integer vectorRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class, uploaded.id().toString());
        assertThat(vectorRowCount).isEqualTo(secondAttemptChunks.size());
    }

    /**
     * Production-readiness audit finding (final verification pass):
     * {@code DocumentRepository.transitionToProcessing}'s {@code WHERE}
     * clause previously omitted {@code PROCESSING} from its {@code IN}
     * list — contradicting {@code findStaleDocumentIds}'s/{@code
     * DocumentRecoveryScheduler}'s own Javadoc, both of which explicitly
     * document recovering a document "stuck in PROCESSING (a crash
     * mid-pipeline)". A document left in {@code PROCESSING} (simulating a
     * crash mid-pipeline, exactly as {@code DocumentRecoveryScheduler}
     * would find it after the configured staleness threshold) could
     * previously never actually be reclaimed — {@code process()} always
     * hit the "not in a processable state" no-op path. This test proves
     * the fix: the exact same {@code process()} call the recovery
     * scheduler itself makes now successfully reclaims and completes a
     * {@code PROCESSING} document.
     */
    @Test
    void aDocumentStuckInProcessingIsReclaimedAndCompletedByTheRecoveryPath() {
        String token = managerToken("rag-stuck-processing@example.com", "Rag Stuck Processing Org");
        DocumentResponse uploaded = upload(token, "notes.txt", "text/plain",
                "A document simulating a crash mid-pipeline must still be recoverable.".getBytes(StandardCharsets.UTF_8));
        awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);

        // Simulate exactly what DocumentRecoveryScheduler would find: a
        // document whose worker died mid-pipeline, left behind in
        // PROCESSING (never FAILED, since a crash never runs the catch
        // block at all).
        forceStatus(uploaded.id(), DocumentStatus.PROCESSING);

        int transitioned = documentRepository.transitionToProcessing(uploaded.id());
        assertThat(transitioned)
                .as("transitionToProcessing must successfully reclaim a PROCESSING row, "
                        + "exactly as DocumentRecoveryScheduler's own Javadoc already promises")
                .isEqualTo(1);

        documentProcessingService.process(uploaded.id());
        DocumentResponse recovered = awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);
        assertThat(recovered.status()).isEqualTo(DocumentStatus.COMPLETED);
    }

    @Test
    void deletingADocumentRemovesItsChunksAndVectors() {
        String token = managerToken("rag-delete@example.com", "Rag Delete Org");
        DocumentResponse uploaded = upload(token, "notes.txt", "text/plain",
                "This document will be deleted after processing completes.".getBytes(StandardCharsets.UTF_8));
        awaitStatus(token, uploaded.id(), DocumentStatus.COMPLETED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/v1/documents/" + uploaded.id()), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UUID organizationId = organizationIdFor("rag-delete@example.com");
        assertThat(documentChunkRepository.findByDocumentAndOrganization(uploaded.id(), organizationId)).isEmpty();

        Integer vectorRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class, uploaded.id().toString());
        assertThat(vectorRowCount).isZero();
    }

    // ---- Helpers -----------------------------------------------------------------

    private DocumentResponse awaitStatus(String token, UUID documentId, DocumentStatus expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            DocumentResponse current = getWithToken("/api/v1/documents/" + documentId, token, DocumentResponse.class)
                    .getBody();
            assertThat(current).isNotNull();
            assertThat(current.status()).isEqualTo(expected);
        });
        return getWithToken("/api/v1/documents/" + documentId, token, DocumentResponse.class).getBody();
    }

    private void forceStatus(UUID documentId, DocumentStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(txStatus -> {
            var document = documentRepository.findById(documentId).orElseThrow();
            document.setStatus(status);
            documentRepository.save(document);
        });
    }

    private UUID organizationIdFor(String email) {
        return new TransactionTemplate(transactionManager).execute(status ->
                userRepository.findByEmailIgnoreCase(email).orElseThrow().getOrganization().getId());
    }

    private DocumentResponse upload(String token, String filename, String contentType, byte[] content) {
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
