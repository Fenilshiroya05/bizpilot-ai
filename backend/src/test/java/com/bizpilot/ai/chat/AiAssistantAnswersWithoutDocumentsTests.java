package com.bizpilot.ai.chat;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.ai.FakeAiInfrastructureTestConfig;
import com.bizpilot.ai.chat.dto.AiChatRequest;
import com.bizpilot.ai.chat.dto.AiChatResponse;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local-chat-only scope, proved end to end at the real Spring context/HTTP
 * level, in an otherwise fully-configured AI environment (same fixture as
 * {@link AiAssistantApiTests} — a real {@code DocumentRetrievalService}
 * backed by a real Testcontainers-Postgres {@code VectorStore} DOES exist
 * here; this is deliberately NOT a "no vector store at all" test, since
 * that combination was investigated and found to require changes to
 * {@code documents.processing.DocumentProcessingService} itself — explicitly
 * out of scope for this change, see the implementation report).
 *
 * <p>What this test proves instead — the part that IS fully in scope: for
 * an organization with zero uploaded documents, {@code POST /api/v1/ai/chat}
 * no longer returns the old deterministic "not enough information" answer
 * without ever calling the model — it now calls the chat model directly
 * with the original question, exactly like {@link
 * DefaultAiAssistantServiceTest#zeroRetrievedChunksStillCallsTheChatModelDirectlyWithTheOriginalMessage()}
 * already proves with Mockito, just verified here against a real HTTP
 * round-trip and a real (empty) vector search.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakeAiInfrastructureTestConfig.class,
        RecordingFakeAiChatServiceTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bizpilot.ai.enabled=true",
        "spring.ai.vectorstore.type=pgvector"
})
class AiAssistantAnswersWithoutDocumentsTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RecordingFakeAiChatService fakeAiChatService;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void aQuestionWithNoUploadedDocumentsStillGetsARealAnswerFromTheChatModel() {
        String token = registerAndLogin("no-documents@example.com", "No Documents Org");
        fakeAiChatService.setCannedAnswer("A direct answer with no document context.");

        ResponseEntity<AiChatResponse> response = postChat(token, "What is BizPilot AI?");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().answer()).isEqualTo("A direct answer with no document context.");
        assertThat(response.getBody().sources()).isEmpty();
        // The exact prior behavior this replaces:
        assertThat(response.getBody().answer())
                .isNotEqualTo("I couldn't find enough information in your organization's documents to answer that.");
    }

    private ResponseEntity<AiChatResponse> postChat(String token, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                url("/api/v1/ai/chat"), new HttpEntity<>(new AiChatRequest(message), headers), AiChatResponse.class);
    }

    private String registerAndLogin(String email, String organizationName) {
        restTemplate.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName), Void.class);
        AuthResponse auth = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
        return auth.accessToken();
    }
}
