package com.bizpilot.crm;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerNoteRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.crm.dto.CustomerUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 7 tenant-isolation / IDOR tests (project instructions §12,
 * §14): an authenticated user from Organization A must never read, modify,
 * or archive Organization B's customer data — by id, by listing, by
 * activities/notes/history, or by attempting to smuggle an organization id
 * through the request body, a query parameter, or a header.
 *
 * <p>Every cross-tenant attempt must fail as 404 (never 403), so a probe
 * never learns whether the target id exists in another tenant at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CustomerTenantIsolationTests {

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
    void organizationACannotReadOrganizationBsCustomerById() {
        String tokenA = managerToken("isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("isoB-read@example.com", "Iso Org B Read");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Customer");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/customers/" + customerB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrganizationBsCustomer() {
        String tokenA = managerToken("isoA-update@example.com", "Iso Org A Update");
        String tokenB = managerToken("isoB-update@example.com", "Iso Org B Update");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Customer To Update");

        CustomerUpdateRequest patch = new CustomerUpdateRequest(
                "Hijacked Name", null, null, null, null, null, null, null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/customers/" + customerB.id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotArchiveOrganizationBsCustomer() {
        String tokenA = managerToken("isoA-archive@example.com", "Iso Org A Archive");
        String tokenB = managerToken("isoB-archive@example.com", "Iso Org B Archive");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Customer To Archive");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/customers/" + customerB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsCustomerActivities() {
        String tokenA = managerToken("isoA-activities@example.com", "Iso Org A Activities");
        String tokenB = managerToken("isoB-activities@example.com", "Iso Org B Activities");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Customer Activities");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/customers/" + customerB.id() + "/activities", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsCustomerNotes() {
        String tokenA = managerToken("isoA-notes@example.com", "Iso Org A Notes");
        String tokenB = managerToken("isoB-notes@example.com", "Iso Org B Notes");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Customer Notes");

        ResponseEntity<ApiError> getResponse = getWithToken(
                "/api/v1/customers/" + customerB.id() + "/notes", tokenA, ApiError.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> postResponse = restTemplate.exchange(
                url("/api/v1/customers/" + customerB.id() + "/notes"), HttpMethod.POST,
                new HttpEntity<>(new CustomerNoteRequest("Injected note"), headers), ApiError.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsCustomerHistory() {
        String tokenA = managerToken("isoA-history@example.com", "Iso Org A History");
        String tokenB = managerToken("isoB-history@example.com", "Iso Org B History");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Customer History");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/customers/" + customerB.id() + "/history", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationAsCustomerListingNeverIncludesOrganizationBsCustomers() {
        String tokenA = managerToken("isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("isoB-listing@example.com", "Iso Org B Listing");
        createCustomer(tokenA, "Org A Only Customer");
        createCustomer(tokenB, "Org B Only Customer");

        ResponseEntity<String> response = getWithToken("/api/v1/customers", tokenA, String.class);

        assertThat(response.getBody()).contains("Org A Only Customer");
        assertThat(response.getBody()).doesNotContain("Org B Only Customer");
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheCreateRequestBody() {
        // CustomerCreateRequest has no organizationId field at all, so there is
        // nothing to smuggle at the DTO level — this proves the resulting
        // customer is still scoped to the caller's real organization even
        // when extra, unrecognized JSON fields are present in the raw body.
        String tokenA = managerToken("isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Bait Customer");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String bodyWithSmuggledOrgId = "{\"name\":\"Smuggled\",\"organizationId\":\"" + customerB.organizationId() + "\"}";

        ResponseEntity<CustomerResponse> response = restTemplate.exchange(
                url("/api/v1/customers"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSmuggledOrgId, headers), CustomerResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotEqualTo(customerB.organizationId());
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsCustomer() {
        String tokenA = managerToken("isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("isoB-query@example.com", "Iso Org B Query");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Query Bait");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/customers/" + customerB.id() + "?organizationId=" + customerB.organizationId(),
                tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsCustomer() {
        String tokenA = managerToken("isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("isoB-header@example.com", "Iso Org B Header");
        CustomerResponse customerB = createCustomer(tokenB, "Org B Header Bait");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", customerB.organizationId().toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/customers/" + customerB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private CustomerResponse createCustomer(String token, String name) {
        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers",
                new CustomerCreateRequest(name, null, null, null, null, null, null), token, CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
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
