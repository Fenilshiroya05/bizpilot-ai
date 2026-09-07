package com.bizpilot.organization;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.dto.UserResponse;
import com.bizpilot.organization.dto.OrganizationResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 5 security tests (CLAUDE.md §7): prove that the current
 * organization is derived exclusively from the authenticated identity, that
 * an authenticated user can never see another organization's data, and that
 * a client-supplied organization id is never trusted.
 *
 * <p>There is no "authenticated user without an organization" scenario to
 * test here — registration always auto-provisions one, and
 * {@code users.organization_id} is a NOT NULL database constraint, so that
 * state cannot occur (see docs/security.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantIsolationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void registrationAutoProvisionsAnOrganizationAndLinksTheUserToIt() {
        RegisterRequest request = new RegisterRequest(
                "owner@example.com", "Passw0rd!", "Owner", "One", "Owner's Company");

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/register"), request, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotNull();
    }

    @Test
    void eachUserSeesOnlyTheirOwnOrganizationNeverAnotherTenants() {
        String tokenA = registerAndLogin("userA@example.com", "Company A Inc");
        String tokenB = registerAndLogin("userB@example.com", "Company B Inc");

        OrganizationResponse orgForA = getCurrentOrganization(tokenA);
        OrganizationResponse orgForB = getCurrentOrganization(tokenB);

        // The literal mandatory attack scenario (CLAUDE.md §7 / project spec §19):
        // User A must never receive Organization B's data, and vice versa.
        assertThat(orgForA.name()).isEqualTo("Company A Inc");
        assertThat(orgForB.name()).isEqualTo("Company B Inc");
        assertThat(orgForA.id()).isNotEqualTo(orgForB.id());
        assertThat(orgForA.name()).isNotEqualTo(orgForB.name());
    }

    @Test
    void aClientSuppliedOrganizationIdCannotOverrideTheAuthenticatedTenant() {
        String tokenA = registerAndLogin("victim@example.com", "Victim Org");
        OrganizationResponse realOrgForA = getCurrentOrganization(tokenA);

        String tokenB = registerAndLogin("attacker@example.com", "Attacker Org");
        OrganizationResponse orgForB = getCurrentOrganization(tokenB);

        // User A calls the endpoint while attempting to smuggle Organization B's id
        // via a header and a query parameter. Neither is ever read by the
        // controller/service — the response must remain scoped to A's own org.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", orgForB.id().toString());

        ResponseEntity<OrganizationResponse> response = restTemplate.exchange(
                url("/api/v1/organizations/current?organizationId=" + orgForB.id()),
                HttpMethod.GET, new HttpEntity<>(headers), OrganizationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(realOrgForA.id());
        assertThat(response.getBody().id()).isNotEqualTo(orgForB.id());
        assertThat(response.getBody().name()).isEqualTo("Victim Org");
    }

    @Test
    void currentOrganizationEndpointRejectsUnauthenticatedRequests() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/v1/organizations/current"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    private String registerAndLogin(String email, String organizationName) {
        RegisterRequest registerRequest = new RegisterRequest(
                email, "Passw0rd!", "First", "Last", organizationName);
        restTemplate.postForEntity(url("/api/v1/auth/register"), registerRequest, UserResponse.class);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class);
        return loginResponse.getBody().accessToken();
    }

    private OrganizationResponse getCurrentOrganization(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<OrganizationResponse> response = restTemplate.exchange(
                url("/api/v1/organizations/current"), HttpMethod.GET, new HttpEntity<>(headers),
                OrganizationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
