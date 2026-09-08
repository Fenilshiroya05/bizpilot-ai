package com.bizpilot.ai.chat;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.ai.FakeAiInfrastructureTestConfig;
import com.bizpilot.ai.chat.dto.AiChatRequest;
import com.bizpilot.ai.chat.dto.AiChatResponse;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end {@code POST /api/v1/ai/chat} tests — real HTTP, real JWT/RBAC,
 * real tenant resolution, real Phase 15 retrieval against a Testcontainers
 * Postgres, and a recording fake {@link AiChatService} standing in for
 * OpenAI (project instructions §65/§66).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakeAiInfrastructureTestConfig.class,
        RecordingFakeAiChatServiceTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bizpilot.ai.enabled=true",
        "spring.ai.vectorstore.type=pgvector"
})
class AiAssistantApiTests {

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
    private RecordingFakeAiChatService fakeAiChatService;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void aQuestionAboutAnIngestedDocumentReturnsAGroundedAnswerWithSources() {
        String token = managerToken("assistant-happy@example.com", "Assistant Happy Org");
        DocumentResponse uploaded = upload(token, "notes.txt",
                "The migration process requires backing up the database before upgrading.");
        awaitCompleted(token, uploaded.id());
        fakeAiChatService.setCannedAnswer("Back up the database first, per the migration guide.");

        ResponseEntity<AiChatResponse> response = postChat(token, "What is the migration process?");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().answer()).isEqualTo("Back up the database first, per the migration guide.");
        assertThat(response.getBody().sources()).isNotEmpty();
        assertThat(response.getBody().sources().get(0).documentId()).isEqualTo(uploaded.id());
        assertThat(response.getBody().sources().get(0).documentName()).isEqualTo("notes.txt");
    }

    @Test
    void aBlankMessageIsRejectedWithValidationError() {
        String token = registerAndLogin("assistant-blank@example.com", "Assistant Blank Org");

        ResponseEntity<ApiError> response = postChatRaw(token, "   ", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void aMessageOverTwoThousandCharactersIsRejected() {
        String token = registerAndLogin("assistant-toolong@example.com", "Assistant Too Long Org");
        String tooLong = "a".repeat(2001);

        ResponseEntity<ApiError> response = postChatRaw(token, tooLong, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/ai/chat"), new AiChatRequest("What does the guide say?"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyExistingRoleCanUseTheAssistant() {
        // AI_USE is granted to all five roles (V4) — spot-check EMPLOYEE,
        // the narrowest role, reaches the endpoint successfully.
        String token = registerAndLogin("assistant-employee@example.com", "Assistant Employee Org");

        ResponseEntity<AiChatResponse> response = postChat(token, "What does the guide say?");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Helpers -----------------------------------------------------------------

    private ResponseEntity<AiChatResponse> postChat(String token, String message) {
        return postChatRaw(token, message, AiChatResponse.class);
    }

    private <T> ResponseEntity<T> postChatRaw(String token, String message, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                url("/api/v1/ai/chat"), new HttpEntity<>(new AiChatRequest(message), headers), responseType);
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

    private String registerAndLogin(String email, String organizationName) {
        register(email, organizationName);
        return login(email).accessToken();
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
