package com.bizpilot.sales;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.dto.UserResponse;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.sales.dto.LeadAssignRequest;
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadNoteRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 8 tenant-isolation / IDOR tests (project instructions §16,
 * §18): an authenticated user from Organization A must never read, modify,
 * archive, or assign Organization B's lead data — by id, by listing, by
 * activities/notes/history, or by attempting to smuggle an organization id
 * through the request body, a query parameter, or a header. Assignment must
 * also reject a cross-organization assignee (a user from Organization B can
 * never be assigned to Organization A's lead).
 *
 * <p>Every cross-tenant attempt must fail as 404 (never 403) for read/write
 * access to the lead itself, so a probe never learns whether the target id
 * exists in another tenant at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LeadTenantIsolationTests {

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
    void organizationACannotReadOrganizationBsLeadById() {
        String tokenA = managerToken("isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("isoB-read@example.com", "Iso Org B Read");
        LeadResponse leadB = createLead(tokenB, "Org B Lead");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/leads/" + leadB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("LEAD_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrganizationBsLead() {
        String tokenA = managerToken("isoA-update@example.com", "Iso Org A Update");
        String tokenB = managerToken("isoB-update@example.com", "Iso Org B Update");
        LeadResponse leadB = createLead(tokenB, "Org B Lead To Update");

        LeadUpdateRequest patch = new LeadUpdateRequest(
                "Hijacked Name", null, null, null, null, null, null, null, false);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/leads/" + leadB.id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotArchiveOrganizationBsLead() {
        String tokenA = managerToken("isoA-archive@example.com", "Iso Org A Archive");
        String tokenB = managerToken("isoB-archive@example.com", "Iso Org B Archive");
        LeadResponse leadB = createLead(tokenB, "Org B Lead To Archive");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/leads/" + leadB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsLeadActivities() {
        String tokenA = managerToken("isoA-activities@example.com", "Iso Org A Activities");
        String tokenB = managerToken("isoB-activities@example.com", "Iso Org B Activities");
        LeadResponse leadB = createLead(tokenB, "Org B Lead Activities");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/leads/" + leadB.id() + "/activities", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAddNotesToOrganizationBsLead() {
        String tokenA = managerToken("isoA-notes@example.com", "Iso Org A Notes");
        String tokenB = managerToken("isoB-notes@example.com", "Iso Org B Notes");
        LeadResponse leadB = createLead(tokenB, "Org B Lead Notes");

        ResponseEntity<ApiError> getResponse = getWithToken(
                "/api/v1/leads/" + leadB.id() + "/notes", tokenA, ApiError.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> postResponse = restTemplate.exchange(
                url("/api/v1/leads/" + leadB.id() + "/notes"), HttpMethod.POST,
                new HttpEntity<>(new LeadNoteRequest("Injected note"), headers), ApiError.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsLeadHistory() {
        String tokenA = managerToken("isoA-history@example.com", "Iso Org A History");
        String tokenB = managerToken("isoB-history@example.com", "Iso Org B History");
        LeadResponse leadB = createLead(tokenB, "Org B Lead History");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/leads/" + leadB.id() + "/history", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAssignOrganizationBsLead() {
        String tokenA = managerToken("isoA-assign-target@example.com", "Iso Org A Assign Target");
        String tokenB = managerToken("isoB-assign-target@example.com", "Iso Org B Assign Target");
        LeadResponse leadB = createLead(tokenB, "Org B Lead To Assign");
        UUID selfAId = getSelf(tokenA).id();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/leads/" + leadB.id() + "/assign"), HttpMethod.POST,
                new HttpEntity<>(new LeadAssignRequest(selfAId), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAssignAUserFromOrganizationBToItsOwnLead() {
        String tokenA = managerToken("isoA-assignee@example.com", "Iso Org A Assignee");
        String tokenB = managerToken("isoB-assignee@example.com", "Iso Org B Assignee");
        LeadResponse leadA = createLead(tokenA, "Org A Lead");
        UUID userBId = getSelf(tokenB).id();

        ResponseEntity<ApiError> response = postWithToken(
                "/api/v1/leads/" + leadA.id() + "/assign", new LeadAssignRequest(userBId), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ASSIGNEE");
    }

    @Test
    void organizationAsLeadListingNeverIncludesOrganizationBsLeads() {
        String tokenA = managerToken("isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("isoB-listing@example.com", "Iso Org B Listing");
        createLead(tokenA, "Org A Only Lead");
        createLead(tokenB, "Org B Only Lead");

        ResponseEntity<String> response = getWithToken("/api/v1/leads", tokenA, String.class);

        assertThat(response.getBody()).contains("Org A Only Lead");
        assertThat(response.getBody()).doesNotContain("Org B Only Lead");
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheCreateRequestBody() {
        // LeadCreateRequest has no organizationId field at all — nothing to
        // smuggle at the DTO level. Proves the resulting lead is still scoped
        // to the caller's real organization even with an extra, unrecognized
        // JSON field present in the raw body.
        String tokenA = managerToken("isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        LeadResponse leadB = createLead(tokenB, "Org B Bait Lead");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String bodyWithSmuggledOrgId = "{\"name\":\"Smuggled\",\"source\":\"OTHER\",\"organizationId\":\""
                + leadB.organizationId() + "\"}";

        ResponseEntity<LeadResponse> response = restTemplate.exchange(
                url("/api/v1/leads"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSmuggledOrgId, headers), LeadResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotEqualTo(leadB.organizationId());
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsLead() {
        String tokenA = managerToken("isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("isoB-query@example.com", "Iso Org B Query");
        LeadResponse leadB = createLead(tokenB, "Org B Query Bait");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/leads/" + leadB.id() + "?organizationId=" + leadB.organizationId(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsLead() {
        String tokenA = managerToken("isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("isoB-header@example.com", "Iso Org B Header");
        LeadResponse leadB = createLead(tokenB, "Org B Header Bait");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", leadB.organizationId().toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/leads/" + leadB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private LeadResponse createLead(String token, String name) {
        ResponseEntity<LeadResponse> response = postWithToken("/api/v1/leads",
                new LeadCreateRequest(name, null, null, null, LeadSource.OTHER, null, null), token, LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private UserResponse getSelf(String token) {
        return getWithToken("/api/v1/auth/me", token, UserResponse.class).getBody();
    }

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
