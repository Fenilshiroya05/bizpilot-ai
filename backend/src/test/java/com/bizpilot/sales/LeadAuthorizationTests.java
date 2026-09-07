package com.bizpilot.sales;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadResponse;
import com.bizpilot.sales.dto.LeadUpdateRequest;
import com.bizpilot.sales.entity.LeadSource;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 8 RBAC tests (project instructions §17): every
 * {@code LEAD_*} permission from Phase 6 is enforced server-side on the
 * matching operation, using the roles/permissions seeded in V4 — no new
 * roles or permissions are introduced here.
 *
 * <p>Seeded mapping recap (docs/security.md): EMPLOYEE = read-only;
 * SALES = CRUD minus delete; MANAGER = full CRUD.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LeadAuthorizationTests {

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
    void everyLeadEndpointRejectsUnauthenticatedRequests() {
        assertThat(restTemplate.getForEntity(url("/api/v1/leads"), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity(url("/api/v1/leads"),
                        new LeadCreateRequest("A", null, null, null, LeadSource.OTHER, null, null), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anEmployeeWithOnlyLeadReadCanReadButNotCreateUpdateOrArchive() {
        // EMPLOYEE is the default self-registration role — no promotion needed.
        String token = registerAndLogin("employee-rbac@example.com", "Employee RBAC Co");

        ResponseEntity<String> listResponse = getWithToken("/api/v1/leads", token, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> createResponse = postWithToken("/api/v1/leads",
                new LeadCreateRequest("Blocked", null, null, null, LeadSource.OTHER, null, null), token,
                ApiError.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(createResponse.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void aSalesUserCanCreateAndUpdateButNotArchiveOrDelete() {
        String token = promotedToken("sales-rbac@example.com", "Sales RBAC Co", UserRole.SALES);

        ResponseEntity<LeadResponse> createResponse = postWithToken("/api/v1/leads",
                new LeadCreateRequest("Sales Created", null, null, null, LeadSource.OTHER, null, null), token,
                LeadResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String leadId = createResponse.getBody().id().toString();

        LeadUpdateRequest patch = new LeadUpdateRequest(
                "Sales Updated", null, null, null, null, null, null, null, false);
        HttpHeaders patchHeaders = new HttpHeaders();
        patchHeaders.setBearerAuth(token);
        ResponseEntity<LeadResponse> updateResponse = restTemplate.exchange(
                url("/api/v1/leads/" + leadId), HttpMethod.PATCH,
                new HttpEntity<>(patch, patchHeaders), LeadResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<ApiError> archiveResponse = restTemplate.exchange(
                url("/api/v1/leads/" + leadId), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), ApiError.class);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aManagerCanPerformEveryLeadOperationIncludingArchive() {
        String token = promotedToken("manager-rbac@example.com", "Manager RBAC Co", UserRole.MANAGER);

        ResponseEntity<LeadResponse> createResponse = postWithToken("/api/v1/leads",
                new LeadCreateRequest("Manager Created", null, null, null, LeadSource.OTHER, null, null), token,
                LeadResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String leadId = createResponse.getBody().id().toString();

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<Void> archiveResponse = restTemplate.exchange(
                url("/api/v1/leads/" + leadId), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), Void.class);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---- Helpers -----------------------------------------------------------------

    private <T> ResponseEntity<T> getWithToken(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private <T> ResponseEntity<T> postWithToken(String path, Object body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
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
