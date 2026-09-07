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
import com.bizpilot.sales.dto.InvoiceCreateRequest;
import com.bizpilot.sales.dto.InvoiceItemRequest;
import com.bizpilot.sales.dto.InvoiceResponse;
import com.bizpilot.sales.dto.InvoiceUpdateRequest;
import com.bizpilot.sales.entity.InvoiceStatus;
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
 * End-to-end CRUD/validation/search/PDF coverage for the Phase 11 Invoice
 * API, against a real PostgreSQL instance — mirrors {@code QuotationApiTests}.
 * Every test operates as a MANAGER (full INVOICE CRUD per V9's mapping)
 * within a single organization — RBAC/tenant-isolation-specific scenarios
 * live in {@link InvoiceAuthorizationTests} and {@link InvoiceTenantIsolationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceApiTests {

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
    void creatingAnInvoiceReturns201AndCalculatesTotalsFromAuthoritativeInputs() {
        String token = managerToken("inv11-creator@example.com", "Creator Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-100", new BigDecimal("50.00"), new BigDecimal("10.00")).id();

        InvoiceCreateRequest request = new InvoiceCreateRequest(customerId, null,
                List.of(new InvoiceItemRequest(productId, new BigDecimal("2"))));
        ResponseEntity<InvoiceResponse> response = postWithToken("/api/v1/invoices", request, token, InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        InvoiceResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(body.subtotal()).isEqualByComparingTo("100.0000");
        assertThat(body.taxAmount()).isEqualByComparingTo("10.0000");
        assertThat(body.total()).isEqualByComparingTo("110.0000");
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().get(0).productNameSnapshot()).isNotBlank();
    }

    @Test
    void frontendSuppliedTotalsAreCompletelyIgnored() {
        String token = managerToken("inv11-spoof@example.com", "Spoof Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-101", new BigDecimal("50.00"), new BigDecimal("10.00")).id();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bodyWithSpoofedTotals = "{"
                + "\"customerId\":\"" + customerId + "\","
                + "\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":2}],"
                + "\"subtotal\":1,\"taxAmount\":1,\"total\":1"
                + "}";

        ResponseEntity<InvoiceResponse> response = restTemplate.exchange(
                url("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSpoofedTotals, headers), InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Correct backend-calculated values (100 subtotal, 10 tax, 110 total),
        // never the spoofed "1" values from the raw request body.
        assertThat(response.getBody().subtotal()).isEqualByComparingTo("100.0000");
        assertThat(response.getBody().taxAmount()).isEqualByComparingTo("10.0000");
        assertThat(response.getBody().total()).isEqualByComparingTo("110.0000");
    }

    @Test
    void creatingWithoutAnyItemsFailsValidation() {
        String token = managerToken("inv11-noitems@example.com", "NoItems Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();

        InvoiceCreateRequest request = new InvoiceCreateRequest(customerId, null, List.of());
        ResponseEntity<ApiError> response = postWithToken("/api/v1/invoices", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void creatingWithAZeroOrNegativeQuantityFailsValidation() {
        String token = managerToken("inv11-badqty@example.com", "BadQty Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-102", new BigDecimal("10.00"), BigDecimal.ZERO).id();

        InvoiceCreateRequest request = new InvoiceCreateRequest(customerId, null,
                List.of(new InvoiceItemRequest(productId, BigDecimal.ZERO)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/invoices", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gettingANonexistentInvoiceReturns404() {
        String token = managerToken("inv11-notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/invoices/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("INVOICE_NOT_FOUND");
    }

    @Test
    void updatingADraftInvoiceReplacesItemsAndRecalculatesTotals() {
        String token = managerToken("inv11-update@example.com", "Update Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-104", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "1");

        UUID newProductId = createProduct(token, "INV-SKU-105", new BigDecimal("40.00"), BigDecimal.ZERO).id();
        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, null,
                List.of(new InvoiceItemRequest(newProductId, new BigDecimal("3"))));
        ResponseEntity<InvoiceResponse> response = patchWithToken(
                "/api/v1/invoices/" + created.id(), patch, token, InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().subtotal()).isEqualByComparingTo("120.0000");
        assertThat(response.getBody().items()).hasSize(1);
    }

    @Test
    void updatingWithStatusCancelledIsRejectedInFavorOfTheDedicatedCancelEndpoint() {
        String token = managerToken("inv11-canceldirect@example.com", "CancelDirect Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-106", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "1");

        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, InvoiceStatus.CANCELLED, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/invoices/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_INVOICE_DATA");
    }

    @Test
    void updatingStatusToIssuedSucceedsWhileStillDraft() {
        String token = managerToken("inv11-statuschange@example.com", "StatusChange Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-107", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "1");

        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, InvoiceStatus.ISSUED, null);
        ResponseEntity<InvoiceResponse> response = patchWithToken(
                "/api/v1/invoices/" + created.id(), patch, token, InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void updatingAnIssuedInvoiceIsRejectedAsAConflict() {
        String token = managerToken("inv11-immutable@example.com", "Immutable Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-112", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "1");
        issueInvoice(token, created.id());

        UUID newProductId = createProduct(token, "INV-SKU-113", new BigDecimal("40.00"), BigDecimal.ZERO).id();
        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, null,
                List.of(new InvoiceItemRequest(newProductId, BigDecimal.ONE)));
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/invoices/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVOICE_NOT_EDITABLE");
    }

    @Test
    void cancellingTransitionsToCancelledAndIsIdempotent() {
        String token = managerToken("inv11-cancel@example.com", "Cancel Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-108", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "1");

        assertThat(deleteWithToken("/api/v1/invoices/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteWithToken("/api/v1/invoices/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<InvoiceResponse> getResponse = getWithToken(
                "/api/v1/invoices/" + created.id(), token, InvoiceResponse.class);
        assertThat(getResponse.getBody().status()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void listingSupportsFilteringByStatusAndCustomer() {
        String token = managerToken("inv11-filters@example.com", "Filters Co");
        UUID customerA = createCustomer(token, "Customer A").id();
        UUID customerB = createCustomer(token, "Customer B").id();
        UUID productId = createProduct(token, "INV-SKU-109", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        InvoiceResponse invoiceA = createInvoice(token, customerA, productId, "1");
        createInvoice(token, customerB, productId, "1");

        JsonNode byCustomer = listInvoices(token, "customerId=" + customerA);
        assertThat(byCustomer.get("content")).hasSize(1);
        assertThat(byCustomer.get("content").get(0).get("id").asText()).isEqualTo(invoiceA.id().toString());

        JsonNode byStatus = listInvoices(token, "status=DRAFT");
        assertThat(byStatus.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevelAndOmitsItems() {
        String token = managerToken("inv11-paginate@example.com", "Paginate Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-110", new BigDecimal("10.00"), BigDecimal.ZERO).id();
        for (int i = 0; i < 5; i++) {
            createInvoice(token, customerId, productId, "1");
        }

        JsonNode firstPage = listInvoices(token, "customerId=" + customerId + "&page=0&size=2");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(firstPage.get("content").get(0).has("items")).isFalse();
    }

    @Test
    void pdfEndpointReturnsAPdfDocument() {
        String token = managerToken("inv11-pdf@example.com", "Pdf Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID productId = createProduct(token, "INV-SKU-111", new BigDecimal("50.00"), new BigDecimal("18.00")).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "3");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url("/api/v1/invoices/" + created.id() + "/pdf"), HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(new String(response.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void pdfEndpointRendersNonWinAnsiCharactersInsteadOfCrashing() {
        String token = managerToken("inv11-pdfunicode@example.com", "Pdf Unicode Co");
        UUID customerId = createCustomer(token, "भारत Traders 🚀").id();
        UUID productId = createProduct(token, "INV-SKU-UNICODE", new BigDecimal("50.00"), new BigDecimal("18.00")).id();
        InvoiceResponse created = createInvoice(token, customerId, productId, "1");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url("/api/v1/invoices/" + created.id() + "/pdf"), HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(response.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void pdfEndpointReturns404ForANonexistentInvoice() {
        String token = managerToken("inv11-pdfnotfound@example.com", "PdfNotFound Co");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/invoices/" + UUID.randomUUID() + "/pdf", token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private InvoiceResponse createInvoice(String token, UUID customerId, UUID productId, String quantity) {
        InvoiceCreateRequest request = new InvoiceCreateRequest(customerId, null,
                List.of(new InvoiceItemRequest(productId, new BigDecimal(quantity))));
        ResponseEntity<InvoiceResponse> response = postWithToken("/api/v1/invoices", request, token, InvoiceResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void issueInvoice(String token, UUID invoiceId) {
        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, InvoiceStatus.ISSUED, null);
        ResponseEntity<InvoiceResponse> response = patchWithToken(
                "/api/v1/invoices/" + invoiceId, patch, token, InvoiceResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private JsonNode listInvoices(String token, String queryString) {
        ResponseEntity<String> response = getWithToken("/api/v1/invoices?" + queryString, token, String.class);
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
