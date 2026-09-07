package com.bizpilot.sales;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.products.dto.ProductCreateRequest;
import com.bizpilot.products.dto.ProductResponse;
import com.bizpilot.sales.dto.QuotationCreateRequest;
import com.bizpilot.sales.dto.QuotationItemRequest;
import com.bizpilot.sales.dto.QuotationResponse;
import com.bizpilot.sales.dto.QuotationUpdateRequest;
import com.bizpilot.sales.entity.QuotationStatus;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CRUD/validation/search/PDF coverage for the Phase 10 Quotation
 * API, against a real PostgreSQL instance. Every test operates as a MANAGER
 * (full QUOTATION CRUD per the already-seeded Phase 6 mapping) within a
 * single organization — RBAC/tenant-isolation-specific scenarios live in
 * {@link QuotationAuthorizationTests} and {@link QuotationTenantIsolationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class QuotationApiTests {

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
    void creatingAQuotationReturns201AndCalculatesTotalsFromAuthoritativeInputs() {
        String token = managerToken("q10-creator@example.com", "Creator Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-100", new BigDecimal("50.00"), new BigDecimal("10.00")).id();

        QuotationCreateRequest request = new QuotationCreateRequest(customerId, null, null,
                List.of(new QuotationItemRequest(productId, new BigDecimal("2"))));
        ResponseEntity<QuotationResponse> response = postWithToken("/api/v1/quotations", request, token, QuotationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        QuotationResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(body.subtotal()).isEqualByComparingTo("100.0000");
        assertThat(body.taxAmount()).isEqualByComparingTo("10.0000");
        assertThat(body.grandTotal()).isEqualByComparingTo("110.0000");
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().get(0).productNameSnapshot()).isNotBlank();
    }

    @Test
    void frontendSuppliedTotalsAreCompletelyIgnored() {
        String token = managerToken("q10-spoof@example.com", "Spoof Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-101", new BigDecimal("50.00"), new BigDecimal("10.00")).id();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bodyWithSpoofedTotals = "{"
                + "\"customerId\":\"" + customerId + "\","
                + "\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":2}],"
                + "\"subtotal\":1,\"discountAmount\":1,\"taxAmount\":1,\"grandTotal\":1"
                + "}";

        ResponseEntity<QuotationResponse> response = restTemplate.exchange(
                url("/api/v1/quotations"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSpoofedTotals, headers), QuotationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Correct backend-calculated values (100 subtotal, 10 tax, 110 total),
        // never the spoofed "1" values from the raw request body.
        assertThat(response.getBody().subtotal()).isEqualByComparingTo("100.0000");
        assertThat(response.getBody().taxAmount()).isEqualByComparingTo("10.0000");
        assertThat(response.getBody().grandTotal()).isEqualByComparingTo("110.0000");
    }

    @Test
    void creatingWithoutAnyItemsFailsValidation() {
        String token = managerToken("q10-noitems@example.com", "NoItems Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();

        QuotationCreateRequest request = new QuotationCreateRequest(customerId, null, null, List.of());
        ResponseEntity<ApiError> response = postWithToken("/api/v1/quotations", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void creatingWithAZeroOrNegativeQuantityFailsValidation() {
        String token = managerToken("q10-badqty@example.com", "BadQty Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-102", new BigDecimal("10.00"), BigDecimal.ZERO).id();

        QuotationCreateRequest request = new QuotationCreateRequest(customerId, null, null,
                List.of(new QuotationItemRequest(productId, BigDecimal.ZERO)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/quotations", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingWithADiscountPercentageOver100FailsValidation() {
        String token = managerToken("q10-baddiscount@example.com", "BadDiscount Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-103", new BigDecimal("10.00"), BigDecimal.ZERO).id();

        QuotationCreateRequest request = new QuotationCreateRequest(customerId, null, new BigDecimal("150"),
                List.of(new QuotationItemRequest(productId, BigDecimal.ONE)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/quotations", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gettingANonexistentQuotationReturns404() {
        String token = managerToken("q10-notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/quotations/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("QUOTATION_NOT_FOUND");
    }

    @Test
    void updatingReplacesItemsAndRecalculatesTotals() {
        String token = managerToken("q10-update@example.com", "Update Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-104", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        QuotationResponse created = createQuotation(token, customerId, productId, "1");

        UUID newProductId = createProduct(token, "SKU-105", new BigDecimal("40.00"), BigDecimal.ZERO).id();
        QuotationUpdateRequest patch = new QuotationUpdateRequest(null, null, false, null, null,
                List.of(new QuotationItemRequest(newProductId, new BigDecimal("3"))));
        ResponseEntity<QuotationResponse> response = patchWithToken(
                "/api/v1/quotations/" + created.id(), patch, token, QuotationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().subtotal()).isEqualByComparingTo("120.0000");
        assertThat(response.getBody().items()).hasSize(1);
    }

    @Test
    void updatingWithStatusCancelledIsRejectedInFavorOfTheDedicatedCancelEndpoint() {
        String token = managerToken("q10-canceldirect@example.com", "CancelDirect Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-106", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        QuotationResponse created = createQuotation(token, customerId, productId, "1");

        QuotationUpdateRequest patch = new QuotationUpdateRequest(
                null, null, false, null, QuotationStatus.CANCELLED, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/quotations/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_QUOTATION_DATA");
    }

    @Test
    void updatingStatusToSentSucceeds() {
        String token = managerToken("q10-statuschange@example.com", "StatusChange Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-107", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        QuotationResponse created = createQuotation(token, customerId, productId, "1");

        QuotationUpdateRequest patch = new QuotationUpdateRequest(
                null, null, false, null, QuotationStatus.SENT, null);
        ResponseEntity<QuotationResponse> response = patchWithToken(
                "/api/v1/quotations/" + created.id(), patch, token, QuotationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(QuotationStatus.SENT);
    }

    @Test
    void cancellingTransitionsToCancelledAndIsIdempotent() {
        String token = managerToken("q10-cancel@example.com", "Cancel Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-108", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        QuotationResponse created = createQuotation(token, customerId, productId, "1");

        assertThat(deleteWithToken("/api/v1/quotations/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteWithToken("/api/v1/quotations/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<QuotationResponse> getResponse = getWithToken(
                "/api/v1/quotations/" + created.id(), token, QuotationResponse.class);
        assertThat(getResponse.getBody().status()).isEqualTo(QuotationStatus.CANCELLED);
    }

    @Test
    void listingSupportsFilteringByStatusAndCustomer() {
        String token = managerToken("q10-filters@example.com", "Filters Co");
        UUID customerA = createCustomer(token, "Customer A").id();
        UUID customerB = createCustomer(token, "Customer B").id();
        UUID productId = createProduct(token, "SKU-109", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        QuotationResponse quoteA = createQuotation(token, customerA, productId, "1");
        createQuotation(token, customerB, productId, "1");

        JsonNode byCustomer = listQuotations(token, "customerId=" + customerA);
        assertThat(byCustomer.get("content")).hasSize(1);
        assertThat(byCustomer.get("content").get(0).get("id").asText()).isEqualTo(quoteA.id().toString());

        JsonNode byStatus = listQuotations(token, "status=DRAFT");
        assertThat(byStatus.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevelAndOmitsItems() {
        String token = managerToken("q10-paginate@example.com", "Paginate Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-110", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        for (int i = 0; i < 5; i++) {
            createQuotation(token, customerId, productId, "1");
        }

        JsonNode firstPage = listQuotations(token, "customerId=" + customerId + "&page=0&size=2");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(firstPage.get("content").get(0).has("items")).isFalse();
    }

    @Test
    void pdfEndpointReturnsAPdfDocument() {
        String token = managerToken("q10-pdf@example.com", "Pdf Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "SKU-111", new BigDecimal("50.00"), new BigDecimal("18.00")).id();
        QuotationResponse created = createQuotation(token, customerId, productId, "3");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url("/api/v1/quotations/" + created.id() + "/pdf"), HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotEmpty();
        // A real PDF starts with the "%PDF-" magic bytes.
        assertThat(new String(response.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void pdfEndpointRendersNonWinAnsiCharactersInsteadOfCrashing() {
        // The Standard 14 PDF fonts (Helvetica) only support WinAnsiEncoding;
        // a security-review finding noted that customer/product names
        // outside it (e.g. non-Latin scripts) previously crashed PDF
        // generation with an uncaught IllegalArgumentException. This
        // customer/product name is chosen to be outside WinAnsiEncoding.
        String token = managerToken("q10-pdfunicode@example.com", "Pdf Unicode Co");
        UUID customerId = createCustomer(token, "भारत Traders 🚀").id();
        UUID productId = createProduct(token, "SKU-UNICODE", new BigDecimal("50.00"), new BigDecimal("18.00")).id();
        QuotationResponse created = createQuotation(token, customerId, productId, "1");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url("/api/v1/quotations/" + created.id() + "/pdf"), HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(response.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void pdfEndpointReturns404ForANonexistentQuotation() {
        String token = managerToken("q10-pdfnotfound@example.com", "PdfNotFound Co");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/quotations/" + UUID.randomUUID() + "/pdf", token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private QuotationResponse createQuotation(String token, UUID customerId, UUID productId, String quantity) {
        QuotationCreateRequest request = new QuotationCreateRequest(customerId, null, null,
                List.of(new QuotationItemRequest(productId, new BigDecimal(quantity))));
        ResponseEntity<QuotationResponse> response = postWithToken("/api/v1/quotations", request, token, QuotationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private CustomerResponse createCustomer(String token, String name) {
        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers",
                new CustomerCreateRequest(name, null, null, null, null, null, null), token, CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ProductResponse createProduct(String token, String sku, BigDecimal price, BigDecimal taxPercentage) {
        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products",
                new ProductCreateRequest(sku, "Product " + sku, null, "pcs", price, taxPercentage, null), token,
                ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JsonNode listQuotations(String token, String queryString) {
        ResponseEntity<String> response = getWithToken("/api/v1/quotations?" + queryString, token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
