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
import com.bizpilot.products.entity.ProductStatus;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CRUD/validation/search coverage for the Phase 9 Product API,
 * against a real PostgreSQL instance. Every test operates as a MANAGER (full
 * PRODUCT CRUD per the Phase 9 seeded mapping) within a single organization —
 * RBAC/tenant-isolation-specific scenarios live in
 * {@link ProductAuthorizationTests} and {@link ProductTenantIsolationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductApiTests {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void creatingAProductReturns201WithLocationAndNormalizesTheSku() {
        String token = managerToken("creator@example.com", "Creator Co");
        ProductCreateRequest request = new ProductCreateRequest(
                "abc-100", "Widget", "A useful widget", "pcs", new BigDecimal("19.99"), new BigDecimal("18.00"), null);

        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products", request, token, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        ProductResponse body = response.getBody();
        assertThat(body.sku()).isEqualTo("ABC-100");
        assertThat(body.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(body.taxPercentage()).isEqualByComparingTo(new BigDecimal("18.00"));
    }

    @Test
    void creatingWithoutASkuFailsValidation() {
        String token = managerToken("nosku@example.com", "NoSku Co");
        ProductCreateRequest request = new ProductCreateRequest(
                "", "Widget", null, "pcs", BigDecimal.TEN, null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/products", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void creatingWithANegativePriceFailsValidation() {
        String token = managerToken("negprice@example.com", "NegPrice Co");
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-NEG", "Widget", null, "pcs", new BigDecimal("-1.00"), null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/products", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingWithAZeroPriceSucceeds() {
        String token = managerToken("zeroprice@example.com", "ZeroPrice Co");
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-ZERO", "Free Widget", null, "pcs", BigDecimal.ZERO, null, null);

        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products", request, token, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().price()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void creatingWithATaxPercentageOver100FailsValidation() {
        String token = managerToken("badtax@example.com", "BadTax Co");
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-TAX", "Widget", null, "pcs", BigDecimal.TEN, new BigDecimal("150"), null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/products", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedStatusValueInARequestBodyReturns400NotAServerError() {
        String token = managerToken("malformed@example.com", "Malformed Co");
        String productId = createProduct(token, "SKU-MAL", "Malformed Target").id().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String malformedBody = "{\"status\":\"NOT_A_REAL_STATUS\"}";

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/products/" + productId), HttpMethod.PATCH,
                new HttpEntity<>(malformedBody, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void gettingANonexistentProductReturns404() {
        String token = managerToken("notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/products/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void creatingADuplicateSkuWithinTheSameOrganizationFails409() {
        String token = managerToken("dup@example.com", "Dup Co");
        createProduct(token, "DUP-SKU", "First");

        ResponseEntity<ApiError> response = postWithToken("/api/v1/products",
                new ProductCreateRequest("dup-sku", "Second", null, "pcs", BigDecimal.TEN, null, null),
                token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_SKU");
    }

    @Test
    void theSameSkuIsAllowedAcrossTwoDifferentOrganizations() {
        String tokenA = managerToken("orgA-dup@example.com", "Org A Dup Co");
        String tokenB = managerToken("orgB-dup@example.com", "Org B Dup Co");

        ResponseEntity<ProductResponse> responseA = postWithToken("/api/v1/products",
                new ProductCreateRequest("SHARED-SKU", "Contact", null, "pcs", BigDecimal.TEN, null, null),
                tokenA, ProductResponse.class);
        ResponseEntity<ProductResponse> responseB = postWithToken("/api/v1/products",
                new ProductCreateRequest("SHARED-SKU", "Contact", null, "pcs", BigDecimal.TEN, null, null),
                tokenB, ProductResponse.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void updatingAppliesOnlyTheSuppliedFieldsLeavingOthersUnchanged() {
        String token = managerToken("partial@example.com", "Partial Co");
        ProductResponse created = createProduct(token, "SKU-PARTIAL", "Original Name");

        ProductUpdateRequest patch = new ProductUpdateRequest(
                null, "Updated Name", null, null, null, null, null, null, false);
        ResponseEntity<ProductResponse> response = patchWithToken(
                "/api/v1/products/" + created.id(), patch, token, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Updated Name");
        assertThat(response.getBody().sku()).isEqualTo(created.sku());
    }

    @Test
    void deletingSetsStatusToInactiveRatherThanRemovingTheProduct() {
        String token = managerToken("delete@example.com", "Delete Co");
        ProductResponse created = createProduct(token, "SKU-DEL", "Delete Target");

        ResponseEntity<Void> deleteResponse = deleteWithToken("/api/v1/products/" + created.id(), token);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProductResponse> getResponse = getWithToken(
                "/api/v1/products/" + created.id(), token, ProductResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void anInactiveProductCanStillBeUpdatedUnlikeArchivedCustomersOrLeads() {
        String token = managerToken("reactivate@example.com", "Reactivate Co");
        ProductResponse created = createProduct(token, "SKU-REACT", "Reactivate Target");
        deleteWithToken("/api/v1/products/" + created.id(), token);

        ProductUpdateRequest reactivate = new ProductUpdateRequest(
                null, null, null, null, null, null, ProductStatus.ACTIVE, null, false);
        ResponseEntity<ProductResponse> response = patchWithToken(
                "/api/v1/products/" + created.id(), reactivate, token, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void deletingIsIdempotent() {
        String token = managerToken("deletetwice@example.com", "DeleteTwice Co");
        ProductResponse created = createProduct(token, "SKU-DEL2", "Delete Twice");

        assertThat(deleteWithToken("/api/v1/products/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteWithToken("/api/v1/products/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void inactiveProductsAppearInDefaultListingUnlikeArchivedCustomersOrLeads() {
        String token = managerToken("listinactive@example.com", "ListInactive Co");
        ProductResponse created = createProduct(token, "SKU-LISTINACT", "List Inactive Target");
        deleteWithToken("/api/v1/products/" + created.id(), token);

        JsonNode defaultListing = listProducts(token, "q=" + encode("List Inactive Target"));

        assertThat(namesIn(defaultListing)).contains("List Inactive Target");
    }

    @Test
    void listingSupportsFreeTextSearchAcrossSkuNameAndDescription() {
        String token = managerToken("search@example.com", "Search Co");
        createProduct(token, "ZEBRA-SKU", "Zebra Product");
        createProduct(token, "ALPHA-SKU", "Alpha Product");

        JsonNode results = listProducts(token, "q=zebra");

        assertThat(namesIn(results)).containsExactly("Zebra Product");
    }

    @Test
    void listingSupportsFilteringByStatusAndUnit() {
        String token = managerToken("filters@example.com", "Filters Co");
        ProductResponse kg = postWithToken("/api/v1/products",
                new ProductCreateRequest("SKU-KG", "Kilo Product", null, "kg", BigDecimal.ONE, null, null),
                token, ProductResponse.class).getBody();
        createProduct(token, "SKU-PCS", "Piece Product");

        JsonNode byUnit = listProducts(token, "unit=kg");
        assertThat(namesIn(byUnit)).containsExactly("Kilo Product");

        JsonNode byStatus = listProducts(token, "status=ACTIVE");
        assertThat(namesIn(byStatus)).contains("Kilo Product", "Piece Product");
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevel() {
        String token = managerToken("paginate@example.com", "Paginate Co");
        for (int i = 0; i < 5; i++) {
            createProduct(token, "SKU-PAGE-" + i, "Paginated Product " + i);
        }

        JsonNode firstPage = listProducts(token, "q=" + encode("Paginated Product") + "&page=0&size=2");
        JsonNode secondPage = listProducts(token, "q=" + encode("Paginated Product") + "&page=1&size=2");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(5);
        assertThat(namesIn(firstPage)).doesNotContainAnyElementsOf(namesIn(secondPage));
    }

    // ---- Categories ---------------------------------------------------------------

    @Test
    void creatingACategoryReturns201() {
        String token = managerToken("category-create@example.com", "Category Create Co");

        ResponseEntity<ProductCategoryResponse> response = postWithToken(
                "/api/v1/products/categories", new ProductCategoryCreateRequest("Electronics"), token,
                ProductCategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Electronics");
    }

    @Test
    void assigningACategoryToAProductSucceedsAndIsReturnedInTheResponse() {
        String token = managerToken("category-assign@example.com", "Category Assign Co");
        ProductCategoryResponse category = postWithToken("/api/v1/products/categories",
                new ProductCategoryCreateRequest("Electronics"), token, ProductCategoryResponse.class).getBody();

        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-CAT", "Categorized Widget", null, "pcs", BigDecimal.TEN, null, category.id());
        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products", request, token, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().categoryId()).isEqualTo(category.id());
    }

    @Test
    void assigningANonexistentCategoryFails400() {
        String token = managerToken("category-invalid@example.com", "Category Invalid Co");
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-BADCAT", "Widget", null, "pcs", BigDecimal.TEN, null, UUID.randomUUID());

        ResponseEntity<ApiError> response = postWithToken("/api/v1/products", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PRODUCT_CATEGORY");
    }

    @Test
    void deletingACategoryClearsItFromReferencingProductsRatherThanBlockingOrCascading() {
        String token = managerToken("category-delete@example.com", "Category Delete Co");
        ProductCategoryResponse category = postWithToken("/api/v1/products/categories",
                new ProductCategoryCreateRequest("Temporary"), token, ProductCategoryResponse.class).getBody();
        ProductResponse product = postWithToken("/api/v1/products",
                new ProductCreateRequest("SKU-CATDEL", "Widget", null, "pcs", BigDecimal.TEN, null, category.id()),
                token, ProductResponse.class).getBody();

        ResponseEntity<Void> deleteResponse = deleteWithToken("/api/v1/products/categories/" + category.id(), token);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProductResponse> getResponse = getWithToken(
                "/api/v1/products/" + product.id(), token, ProductResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().categoryId()).isNull();
    }

    @Test
    void categoryRouteDoesNotConflictWithTheProductByIdRoute() {
        String token = managerToken("route-conflict@example.com", "Route Conflict Co");

        ResponseEntity<String> categoriesResponse = getWithToken("/api/v1/products/categories", token, String.class);

        assertThat(categoriesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatingACategoryAppliesTheNewName() {
        String token = managerToken("category-update@example.com", "Category Update Co");
        ProductCategoryResponse category = postWithToken("/api/v1/products/categories",
                new ProductCategoryCreateRequest("Old Name"), token, ProductCategoryResponse.class).getBody();

        ResponseEntity<ProductCategoryResponse> response = patchWithToken(
                "/api/v1/products/categories/" + category.id(), new ProductCategoryUpdateRequest("New Name"), token,
                ProductCategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("New Name");
    }

    // ---- Helpers -----------------------------------------------------------------

    private ProductResponse createProduct(String token, String sku, String name) {
        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products",
                new ProductCreateRequest(sku, name, null, "pcs", BigDecimal.TEN, null, null), token,
                ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JsonNode listProducts(String token, String queryString) {
        ResponseEntity<String> response = getWithToken("/api/v1/products?" + queryString, token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> namesIn(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(node -> names.add(node.get("name").asText()));
        return names;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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

    private <T> ResponseEntity<T> patchWithToken(String path, Object body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.PATCH, new HttpEntity<>(body, headers), responseType);
    }

    private ResponseEntity<Void> deleteWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
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
