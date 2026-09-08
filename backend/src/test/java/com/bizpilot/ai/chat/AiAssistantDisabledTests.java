package com.bizpilot.ai.chat;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.ai.chat.dto.AiChatRequest;
import com.bizpilot.common.response.ApiError;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code POST /api/v1/ai/chat} endpoint's disabled-AI behavior
 * (project instructions §27/§70) using the project's own DEFAULT test
 * configuration — {@code bizpilot.ai.enabled=false}, exactly like the vast
 * majority of the suite — deliberately NOT importing {@code
 * FakeAiInfrastructureTestConfig}, proving this endpoint requires no
 * OpenAI/PGVector-related bean at all to return a controlled response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AiAssistantDisabledTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void returnsServiceUnavailableWithoutAttemptingRetrievalOrAProviderCall() {
        String token = registerAndLogin("assistant-disabled@example.com", "Assistant Disabled Org");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/ai/chat"), new HttpEntity<>(new AiChatRequest("What does the guide say?"), headers),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("AI_DISABLED");
    }

    @Test
    void applicationHealthIsUnaffectedByTheDisabledAssistant() {
        // Confirms the endpoint's mere existence never required an
        // OPENAI_API_KEY or a live PGVector bean at startup.
        ResponseEntity<String> health = restTemplate.getForEntity(url("/actuator/health"), String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String registerAndLogin(String email, String organizationName) {
        restTemplate.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName), Void.class);
        AuthResponse auth = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
        return auth.accessToken();
    }
}
