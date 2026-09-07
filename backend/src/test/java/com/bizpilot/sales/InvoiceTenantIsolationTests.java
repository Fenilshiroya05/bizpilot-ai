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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 11 tenant-isolation / IDOR tests — mirrors
 * {@code QuotationTenantIsolationTests}. An authenticated user from
 * Organization A must never read, modify, or cancel Organization B's
 * invoices, and must never be able to attach Organization B's customer or
 * product to an invoice of their own.
 *
 * <p>Every cross-tenant attempt against the invoice resource itself must
 * fail as 404 (never 403). Cross-org customer/product *references* fail as
 * 400 — the referenced entity's existence isn't itself tenant-secret the way
 * the invoice resource is.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceTenantIsolationTests {

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
    void organizationACannotReadOrganizationBsInvoice() {
        String tokenA = managerToken("inv11-isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("inv11-isoB-read@example.com", "Iso Org B Read");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/invoices/" + invoiceB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("INVOICE_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrganizationBsInvoice() {
        String tokenA = managerToken("inv11-isoA-update@example.com", "Iso Org A Update");
        String tokenB = managerToken("inv11-isoB-update@example.com", "Iso Org B Update");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, true, null, null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/invoices/" + invoiceB.id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotCancelOrganizationBsInvoice() {
        String tokenA = managerToken("inv11-isoA-cancel@example.com", "Iso Org A Cancel");
        String tokenB = managerToken("inv11-isoB-cancel@example.com", "Iso Org B Cancel");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/invoices/" + invoiceB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsInvoicePdf() {
        String tokenA = managerToken("inv11-isoA-pdf@example.com", "Iso Org A Pdf");
        String tokenB = managerToken("inv11-isoB-pdf@example.com", "Iso Org B Pdf");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/invoices/" + invoiceB.id() + "/pdf", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationAsInvoiceListingNeverIncludesOrganizationBsInvoices() {
        String tokenA = managerToken("inv11-isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("inv11-isoB-listing@example.com", "Iso Org B Listing");
        InvoiceResponse invoiceA = createInvoiceForOwnOrg(tokenA);
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        ResponseEntity<String> response = getWithToken("/api/v1/invoices", tokenA, String.class);

        assertThat(response.getBody()).contains(invoiceA.id().toString());
        assertThat(response.getBody()).doesNotContain(invoiceB.id().toString());
    }

    @Test
    void organizationACannotAttachACustomerFromOrganizationBToItsOwnInvoice() {
        String tokenA = managerToken("inv11-isoA-customer@example.com", "Iso Org A Customer");
        String tokenB = managerToken("inv11-isoB-customer@example.com", "Iso Org B Customer");
        UUID customerB = createCustomer(tokenB, "Org B Customer").id();
        UUID productA = createProduct(tokenA, "INV-ISOA-PROD-CUST").id();

        InvoiceCreateRequest request = new InvoiceCreateRequest(customerB, null,
                List.of(new InvoiceItemRequest(productA, BigDecimal.ONE)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/invoices", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void organizationACannotAttachAProductFromOrganizationBToItsOwnInvoice() {
        String tokenA = managerToken("inv11-isoA-product@example.com", "Iso Org A Product");
        String tokenB = managerToken("inv11-isoB-product@example.com", "Iso Org B Product");
        UUID customerA = createCustomer(tokenA, "Org A Customer").id();
        UUID productB = createProduct(tokenB, "INV-ISOB-PROD").id();

        InvoiceCreateRequest request = new InvoiceCreateRequest(customerA, null,
                List.of(new InvoiceItemRequest(productB, BigDecimal.ONE)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/invoices", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PRODUCT_REFERENCE");
    }

    @Test
    void organizationACannotReassignAnInvoiceToACustomerFromOrganizationBViaUpdate() {
        String tokenA = managerToken("inv11-isoA-reassign@example.com", "Iso Org A Reassign");
        String tokenB = managerToken("inv11-isoB-reassign@example.com", "Iso Org B Reassign");
        InvoiceResponse invoiceA = createInvoiceForOwnOrg(tokenA);
        UUID customerB = createCustomer(tokenB, "Org B Customer").id();

        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(customerB, null, false, null, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/invoices/" + invoiceA.id(), patch, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheCreateRequestBody() {
        String tokenA = managerToken("inv11-isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("inv11-isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);
        UUID customerA = createCustomer(tokenA, "Org A Customer").id();
        UUID productA = createProduct(tokenA, "INV-ISOA-SMUGGLE").id();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String bodyWithSmuggledOrgId = "{\"customerId\":\"" + customerA + "\","
                + "\"items\":[{\"productId\":\"" + productA + "\",\"quantity\":1}],"
                + "\"organizationId\":\"" + invoiceB.organizationId() + "\"}";

        ResponseEntity<InvoiceResponse> response = restTemplate.exchange(
                url("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSmuggledOrgId, headers), InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotEqualTo(invoiceB.organizationId());
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsInvoice() {
        String tokenA = managerToken("inv11-isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("inv11-isoB-query@example.com", "Iso Org B Query");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/invoices/" + invoiceB.id() + "?organizationId=" + invoiceB.organizationId(),
                tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsInvoice() {
        String tokenA = managerToken("inv11-isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("inv11-isoB-header@example.com", "Iso Org B Header");
        InvoiceResponse invoiceB = createInvoiceForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", invoiceB.organizationId().toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/invoices/" + invoiceB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private InvoiceResponse createInvoiceForOwnOrg(String token) {
        UUID customerId = createCustomer(token, "Contact").id();
        UUID productId = createProduct(token, "INV-SKU-" + UUID.randomUUID()).id();
        InvoiceCreateRequest request = new InvoiceCreateRequest(customerId, null,
                List.of(new InvoiceItemRequest(productId, BigDecimal.ONE)));
        ResponseEntity<InvoiceResponse> response = postWithToken("/api/v1/invoices", request, token, InvoiceResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private CustomerResponse createCustomer(String token, String name) {
        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers",
                new CustomerCreateRequest(name, null, null, null, null, null, null), token, CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ProductResponse createProduct(String token, String sku) {
        ResponseEntity<ProductResponse> response = postWithToken("/api/v1/products",
                new ProductCreateRequest(sku, "Product " + sku, null, "pcs", BigDecimal.TEN, null, null), token,
                ProductResponse.class);
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

    private <T> ResponseEntity<T> patchWithToken(String path, Object body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.PATCH, new HttpEntity<>(body, headers), responseType);
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
