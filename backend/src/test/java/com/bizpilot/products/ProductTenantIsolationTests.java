package com.bizpilot.products;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.products.dto.ProductCategoryCreateRequest;
import com.bizpilot.products.dto.ProductCategoryResponse;
import com.bizpilot.products.dto.ProductCategoryUpdateRequest;
import com.bizpilot.products.dto.ProductCreateRequest;
import com.bizpilot.products.dto.ProductResponse;
import com.bizpilot.products.dto.ProductUpdateRequest;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 9 tenant-isolation / IDOR tests (project instructions §12):
 * an authenticated user from Organization A must never read, modify, or
 * delete Organization B's products or categories — by id, by listing, or by
 * attempting to smuggle an organization id through the request body, a query
 * parameter, or a header. A product must never be assignable to a category
 * belonging to another organization.
 *
 * <p>Every cross-tenant attempt must fail as 404 (never 403), so a probe
 * never learns whether the target id exists in another tenant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductTenantIsolationTests {

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
    void organizationACannotReadOrganizationBsProductById() {
        String tokenA = managerToken("isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("isoB-read@example.com", "Iso Org B Read");
        ProductResponse productB = createProduct(tokenB, "ISOB-READ", "Org B Product");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/products/" + productB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrganizationBsProduct() {
        String tokenA = managerToken("isoA-update@example.com", "Iso Org A Update");
        String tokenB = managerToken("isoB-update@example.com", "Iso Org B Update");
        ProductResponse productB = createProduct(tokenB, "ISOB-UPDATE", "Org B Product To Update");

        ProductUpdateRequest patch = new ProductUpdateRequest(
                null, "Hijacked Name", null, null, null, null, null, null, false);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/products/" + productB.id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotDeleteOrganizationBsProduct() {
        String tokenA = managerToken("isoA-delete@example.com", "Iso Org A Delete");
        String tokenB = managerToken("isoB-delete@example.com", "Iso Org B Delete");
        ProductResponse productB = createProduct(tokenB, "ISOB-DELETE", "Org B Product To Delete");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/products/" + productB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationAsProductListingNeverIncludesOrganizationBsProducts() {
        String tokenA = managerToken("isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("isoB-listing@example.com", "Iso Org B Listing");
        createProduct(tokenA, "ISOA-LIST", "Org A Only Product");
        createProduct(tokenB, "ISOB-LIST", "Org B Only Product");

        ResponseEntity<String> response = getWithToken("/api/v1/products", tokenA, String.class);

        assertThat(response.getBody()).contains("Org A Only Product");
        assertThat(response.getBody()).doesNotContain("Org B Only Product");
    }

    @Test
    void organizationACannotReadOrganizationBsCategory() {
        String tokenA = managerToken("isoA-cat-read@example.com", "Iso Org A Cat Read");
        String tokenB = managerToken("isoB-cat-read@example.com", "Iso Org B Cat Read");
        ProductCategoryResponse categoryB = createCategory(tokenB, "Org B Category");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/products/categories/" + categoryB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_CATEGORY_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrDeleteOrganizationBsCategory() {
        String tokenA = managerToken("isoA-cat-mod@example.com", "Iso Org A Cat Mod");
        String tokenB = managerToken("isoB-cat-mod@example.com", "Iso Org B Cat Mod");
        ProductCategoryResponse categoryB = createCategory(tokenB, "Org B Category To Modify");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> updateResponse = restTemplate.exchange(
                url("/api/v1/products/categories/" + categoryB.id()), HttpMethod.PATCH,
                new HttpEntity<>(new ProductCategoryUpdateRequest("Hijacked"), headers), ApiError.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> deleteResponse = restTemplate.exchange(
                url("/api/v1/products/categories/" + categoryB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAssignACategoryFromOrganizationBToItsOwnProduct() {
        String tokenA = managerToken("isoA-cat-assign@example.com", "Iso Org A Cat Assign");
        String tokenB = managerToken("isoB-cat-assign@example.com", "Iso Org B Cat Assign");
        ProductCategoryResponse categoryB = createCategory(tokenB, "Org B Bait Category");

        ProductCreateRequest request = new ProductCreateRequest(
                "ISOA-CATASSIGN", "Widget", null, "pcs", BigDecimal.TEN, null, categoryB.id());
        ResponseEntity<ApiError> response = postWithToken("/api/v1/products", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PRODUCT_CATEGORY");
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheCreateRequestBody() {
        // ProductCreateRequest has no organizationId field at all — nothing to
        // smuggle at the DTO level. Proves the resulting product is still
        // scoped to the caller's real organization even with an extra,
        // unrecognized JSON field present in the raw body.
        String tokenA = managerToken("isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        ProductResponse productB = createProduct(tokenB, "ISOB-MASSASSIGN", "Org B Bait Product");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String bodyWithSmuggledOrgId = "{\"sku\":\"SMUGGLED\",\"name\":\"Smuggled\",\"unit\":\"pcs\",\"price\":1,"
                + "\"organizationId\":\"" + productB.organizationId() + "\"}";

        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                url("/api/v1/products"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSmuggledOrgId, headers), ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotEqualTo(productB.organizationId());
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsProduct() {
        String tokenA = managerToken("isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("isoB-query@example.com", "Iso Org B Query");
        ProductResponse productB = createProduct(tokenB, "ISOB-QUERY", "Org B Query Bait");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/products/" + productB.id() + "?organizationId=" + productB.organizationId(),
                tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsProduct() {
        String tokenA = managerToken("isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("isoB-header@example.com", "Iso Org B Header");
        ProductResponse productB = createProduct(tokenB, "ISOB-HEADER", "Org B Header Bait");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", productB.organizationId().toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/products/" + productB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private ProductResponse createProduct(String token, String sku, String name) {
        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products",
                new ProductCreateRequest(sku, name, null, "pcs", BigDecimal.TEN, null, null), token,
                ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ProductCategoryResponse createCategory(String token, String name) {
        ResponseEntity<ProductCategoryResponse> response = postWithToken(
                "/api/v1/products/categories", new ProductCategoryCreateRequest(name), token,
                ProductCategoryResponse.class);
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
