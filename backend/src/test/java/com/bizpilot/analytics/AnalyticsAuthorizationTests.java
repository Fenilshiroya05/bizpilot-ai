package com.bizpilot.analytics;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
import com.bizpilot.common.response.ApiError;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 19 RBAC tests (project instructions §8/§9/§34):
 * {@code ANALYTICS_READ} — seeded by V13 for every existing role — is
 * independently enforced server-side on {@code GET /api/v1/analytics/summary}.
 * Since every seeded role receives {@code ANALYTICS_READ} (a locked Phase 19
 * decision, unlike Phase 17/18's single-permission-missing tests), there is
 * no seeded role to prove a "missing ANALYTICS_READ but authenticated" case
 * with — per project instructions §34 ("do not create test-only production
 * permissions"), that case is intentionally not fabricated here; every
 * registered/authenticated user in this system always has it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AnalyticsAuthorizationTests {

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
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/analytics/summary"), HttpMethod.GET, HttpEntity.EMPTY, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anEmployeeTheDefaultSelfRegistrationRoleHasAnalyticsReadAndSucceeds() {
        // EMPLOYEE is the default self-registration role — no promotion
        // needed — proving ANALYTICS_READ really was seeded for it, not
        // just for the more-privileged roles exercised elsewhere.
        String token = registerAndLogin("analytics-employee@example.com", "Analytics Employee Org");

        ResponseEntity<AnalyticsSummaryResponse> response = getWithToken(token, AnalyticsSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void everySeededRoleHasAnalyticsReadAndSucceeds() {
        for (UserRole role : UserRole.values()) {
            String token = promotedToken("analytics-" + role.name().toLowerCase() + "@example.com",
                    "Analytics " + role.name() + " Org", role);

            ResponseEntity<AnalyticsSummaryResponse> response = getWithToken(token, AnalyticsSummaryResponse.class);

            assertThat(response.getStatusCode())
                    .as("role %s must have ANALYTICS_READ", role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * Phase 26: the same {@code ANALYTICS_READ} gate, independently proven
     * on every new chart/widget endpoint — not just {@code /summary}.
     */
    private static final List<String> PHASE_26_ENDPOINTS = List.of(
            "/api/v1/analytics/revenue-trend",
            "/api/v1/analytics/lead-funnel",
            "/api/v1/analytics/lead-sources",
            "/api/v1/analytics/sales-pipeline",
            "/api/v1/analytics/top-customers");

    @Test
    void everyPhase26EndpointRejectsAnUnauthenticatedRequest() {
        for (String path : PHASE_26_ENDPOINTS) {
            ResponseEntity<ApiError> response = restTemplate.exchange(
                    url(path), HttpMethod.GET, HttpEntity.EMPTY, ApiError.class);

            assertThat(response.getStatusCode()).as("path %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void everySeededRoleHasAnalyticsReadOnEveryPhase26Endpoint() {
        for (UserRole role : UserRole.values()) {
            String token = promotedToken("analytics26-" + role.name().toLowerCase() + "@example.com",
                    "Analytics 26 " + role.name() + " Org", role);

            for (String path : PHASE_26_ENDPOINTS) {
                ResponseEntity<String> response = getPathWithToken(token, path);

                assertThat(response.getStatusCode())
                        .as("role %s must have ANALYTICS_READ on %s", role, path)
                        .isEqualTo(HttpStatus.OK);
            }
        }
    }

    // ---- Helpers -----------------------------------------------------------------

    private <T> ResponseEntity<T> getWithToken(String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url("/api/v1/analytics/summary"), HttpMethod.GET,
                new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<String> getPathWithToken(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
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
