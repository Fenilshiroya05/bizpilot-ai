package com.bizpilot.ai.scoring;

import com.bizpilot.TestcontainersConfiguration;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code POST /api/v1/leads/{id}/score}'s disabled-AI behavior
 * (project instructions §8/§40) using the project's own DEFAULT test
 * configuration — {@code bizpilot.ai.enabled=false}, exactly like the vast
 * majority of the suite — mirrors {@code ai.chat.AiAssistantDisabledTests}.
 *
 * <p>Uses a random, nonexistent lead id deliberately: {@link
 * LeadScoringService#score} checks AI availability <em>before</em> ever
 * calling {@code LeadService.getById} (project instructions §8), so a
 * random id still returns {@code 503}/{@code AI_DISABLED} rather than
 * {@code 404}/{@code LEAD_NOT_FOUND} — proving the ordering directly,
 * without needing to create a real lead or introduce an awkward test-only
 * seam purely to observe call order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LeadScoringDisabledTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void returnsServiceUnavailableBeforeAttemptingAnyLeadLookupOrProviderCall() {
        String token = registerAndLogin("scoring-disabled@example.com", "Scoring Disabled Org");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/leads/" + UUID.randomUUID() + "/score"), HttpMethod.POST,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("AI_DISABLED");
    }

    private String registerAndLogin(String email, String organizationName) {
        restTemplate.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName), Void.class);
        AuthResponse auth = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
        return auth.accessToken();
    }
}
