package com.bizpilot.security;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.entity.Role;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RefreshTokenRequest;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 6 security tests: permission-based authorization, privilege
 * escalation resistance, and the combined tenant+RBAC scenario (a permission
 * never grants access to another organization's data — CLAUDE.md §7 + §9
 * together).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RbacAuthorizationTests {

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

    // ---- Permission-based authorization ---------------------------------------

    @Test
    void permissionGatedEndpointAllowsARoleThatHasThePermission() {
        // MANAGER has CUSTOMER_DELETE per the seeded mapping (docs/security.md).
        register("manager@example.com", "Manager Co");
        assignRole("manager@example.com", UserRole.MANAGER);
        String token = login("manager@example.com").accessToken();

        ResponseEntity<String> response = getWithToken("/api/v1/test/customer-delete-only", token, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void permissionGatedEndpointForbidsARoleWithoutThePermission() {
        // EMPLOYEE (the default self-registration role) does NOT have CUSTOMER_DELETE.
        register("employee@example.com", "Employee Co");
        String token = login("employee@example.com").accessToken();

        ResponseEntity<ApiError> response = getWithToken("/api/v1/test/customer-delete-only", token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void permissionGatedEndpointRejectsUnauthenticatedRequests() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/v1/test/customer-delete-only"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Tenant + RBAC combined -------------------------------------------------

    @Test
    void havingThePermissionNeverGrantsAccessToAnotherOrganizationsData() {
        // Both users get CUSTOMER_READ via the default EMPLOYEE role — the point
        // is that possessing the permission never substitutes for, or bypasses,
        // tenant resolution.
        register("orgA-user@example.com", "Org A Inc");
        register("orgB-user@example.com", "Org B Inc");
        String tokenA = login("orgA-user@example.com").accessToken();
        String tokenB = login("orgB-user@example.com").accessToken();

        ResponseEntity<Map> responseA = getWithToken("/api/v1/test/customer-read-only", tokenA, Map.class);
        ResponseEntity<Map> responseB = getWithToken("/api/v1/test/customer-read-only", tokenB, Map.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.OK);
        String orgIdForA = (String) responseA.getBody().get("organizationId");
        String orgIdForB = (String) responseB.getBody().get("organizationId");

        assertThat(orgIdForA).isNotEqualTo(orgIdForB);
    }

    // ---- Privilege escalation ---------------------------------------------------

    @Test
    void tamperingWithTheAuthoritiesClaimInvalidatesTheTokensSignature() {
        register("victim@example.com", "Victim Co");
        String token = login("victim@example.com").accessToken();

        String[] parts = token.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // Escalate a plain EMPLOYEE token to also claim ROLE_OWNER.
        String tamperedPayloadJson = payloadJson.replace("ROLE_EMPLOYEE", "ROLE_OWNER");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        ResponseEntity<ApiError> response = getWithToken("/api/v1/test/admin-only", tamperedToken, ApiError.class);

        // Rejected as unauthenticated (invalid signature), never authorized as OWNER/ADMIN.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void grantingAnAdditionalRoleTakesEffectOnlyAfterRefreshNotOnTheOldToken() {
        String email = "promoted@example.com";
        register(email, "Promoted Co");
        AuthResponse loginResponse = login(email);

        // Old token: EMPLOYEE only, admin-only endpoint must still be forbidden.
        ResponseEntity<ApiError> beforePromotion = getWithToken(
                "/api/v1/test/admin-only", loginResponse.accessToken(), ApiError.class);
        assertThat(beforePromotion.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // An admin promotes the user directly in the authoritative RBAC model.
        assignAdditionalRole(email, UserRole.ADMIN);

        // The OLD access token is unaffected (authorities were baked in at issuance).
        ResponseEntity<ApiError> stillOldToken = getWithToken(
                "/api/v1/test/admin-only", loginResponse.accessToken(), ApiError.class);
        assertThat(stillOldToken.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Refreshing re-resolves authorities from the current (promoted) DB state.
        ResponseEntity<AuthResponse> refreshed = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(loginResponse.refreshToken()), AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> afterPromotion = getWithToken(
                "/api/v1/test/admin-only", refreshed.getBody().accessToken(), String.class);
        assertThat(afterPromotion.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void revokingARoleTakesEffectOnRefreshRemovingAccessGrantedByIt() {
        String email = "demoted@example.com";
        register(email, "Demoted Co");
        assignAdditionalRole(email, UserRole.ADMIN);

        AuthResponse loginAsAdmin = login(email);
        ResponseEntity<String> whileAdmin = getWithToken(
                "/api/v1/test/admin-only", loginAsAdmin.accessToken(), String.class);
        assertThat(whileAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Revoke the ADMIN role, keeping only EMPLOYEE.
        revokeRole(email, UserRole.ADMIN);

        ResponseEntity<AuthResponse> refreshed = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(loginAsAdmin.refreshToken()), AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> afterRevocation = getWithToken(
                "/api/v1/test/admin-only", refreshed.getBody().accessToken(), ApiError.class);
        assertThat(afterRevocation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- Helpers -----------------------------------------------------------------

    private <T> ResponseEntity<T> getWithToken(String path, String accessToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private void register(String email, String organizationName) {
        RegisterRequest registerRequest = new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName);
        restTemplate.postForEntity(url("/api/v1/auth/register"), registerRequest, Void.class);
    }

    private AuthResponse login(String email) {
        return restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
    }

    /**
     * Replaces the user's role(s) with exactly the given one (simulates an
     * admin reassignment). Runs in its own explicit, committing transaction
     * (not {@code @Transactional} on the test method) — the test methods
     * using this also make real HTTP calls that must observe the change
     * through a separate connection, so it has to actually commit rather
     * than stay open/roll back with the test.
     */
    private void assignRole(String email, UserRole role) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            Role roleEntity = roleRepository.findByName(role.name()).orElseThrow();
            user.getRoles().clear();
            user.getRoles().add(roleEntity);
            userRepository.saveAndFlush(user);
        });
    }

    /** Adds a role on top of whatever the user already has (simulates an admin promotion). */
    private void assignAdditionalRole(String email, UserRole role) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            Role roleEntity = roleRepository.findByName(role.name()).orElseThrow();
            user.getRoles().add(roleEntity);
            userRepository.saveAndFlush(user);
        });
    }

    /** Removes a role from the user (simulates an admin revocation). */
    private void revokeRole(String email, UserRole role) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            Role roleEntity = roleRepository.findByName(role.name()).orElseThrow();
            user.getRoles().remove(roleEntity);
            userRepository.saveAndFlush(user);
        });
    }
}
