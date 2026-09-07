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
 * Mandatory Phase 10 tenant-isolation / IDOR tests (project instructions
 * §17): an authenticated user from Organization A must never read, modify,
 * or cancel Organization B's quotations, and must never be able to attach
 * Organization B's customer or product to a quotation of their own.
 *
 * <p>Every cross-tenant attempt against the quotation resource itself must
 * fail as 404 (never 403), so a probe never learns whether the target id
 * exists in another tenant. Cross-org customer/product *references* fail as
 * 400 (same reasoning as Lead's assignee validation, Phase 8, and Product's
 * category validation, Phase 9) — the referenced entity's existence isn't
 * itself tenant-secret the way the quotation resource is.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class QuotationTenantIsolationTests {

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
    void organizationACannotReadOrganizationBsQuotation() {
        String tokenA = managerToken("q10-isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("q10-isoB-read@example.com", "Iso Org B Read");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/quotations/" + quotationB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("QUOTATION_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrganizationBsQuotation() {
        String tokenA = managerToken("q10-isoA-update@example.com", "Iso Org A Update");
        String tokenB = managerToken("q10-isoB-update@example.com", "Iso Org B Update");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        QuotationUpdateRequest patch = new QuotationUpdateRequest(
                null, null, false, new BigDecimal("50"), null, null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/quotations/" + quotationB.id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotCancelOrganizationBsQuotation() {
        String tokenA = managerToken("q10-isoA-cancel@example.com", "Iso Org A Cancel");
        String tokenB = managerToken("q10-isoB-cancel@example.com", "Iso Org B Cancel");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/quotations/" + quotationB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAccessOrganizationBsQuotationPdf() {
        String tokenA = managerToken("q10-isoA-pdf@example.com", "Iso Org A Pdf");
        String tokenB = managerToken("q10-isoB-pdf@example.com", "Iso Org B Pdf");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/quotations/" + quotationB.id() + "/pdf", tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationAsQuotationListingNeverIncludesOrganizationBsQuotations() {
        String tokenA = managerToken("q10-isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("q10-isoB-listing@example.com", "Iso Org B Listing");
        QuotationResponse quotationA = createQuotationForOwnOrg(tokenA);
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        ResponseEntity<String> response = getWithToken("/api/v1/quotations", tokenA, String.class);

        assertThat(response.getBody()).contains(quotationA.id().toString());
        assertThat(response.getBody()).doesNotContain(quotationB.id().toString());
    }

    @Test
    void organizationACannotAttachACustomerFromOrganizationBToItsOwnQuotation() {
        String tokenA = managerToken("q10-isoA-customer@example.com", "Iso Org A Customer");
        String tokenB = managerToken("q10-isoB-customer@example.com", "Iso Org B Customer");
        UUID customerB = createCustomer(tokenB, "Org B Customer").id();
        UUID productA = createProduct(tokenA, "ISOA-PROD-CUST").id();

        QuotationCreateRequest request = new QuotationCreateRequest(customerB, null, null,
                List.of(new QuotationItemRequest(productA, BigDecimal.ONE)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/quotations", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void organizationACannotAttachAProductFromOrganizationBToItsOwnQuotation() {
        String tokenA = managerToken("q10-isoA-product@example.com", "Iso Org A Product");
        String tokenB = managerToken("q10-isoB-product@example.com", "Iso Org B Product");
        UUID customerA = createCustomer(tokenA, "Org A Customer").id();
        UUID productB = createProduct(tokenB, "ISOB-PROD").id();

        QuotationCreateRequest request = new QuotationCreateRequest(customerA, null, null,
                List.of(new QuotationItemRequest(productB, BigDecimal.ONE)));
        ResponseEntity<ApiError> response = postWithToken("/api/v1/quotations", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PRODUCT_REFERENCE");
    }

    @Test
    void organizationACannotReassignAQuotationToACustomerFromOrganizationBViaUpdate() {
        String tokenA = managerToken("q10-isoA-reassign@example.com", "Iso Org A Reassign");
        String tokenB = managerToken("q10-isoB-reassign@example.com", "Iso Org B Reassign");
        QuotationResponse quotationA = createQuotationForOwnOrg(tokenA);
        UUID customerB = createCustomer(tokenB, "Org B Customer").id();

        QuotationUpdateRequest patch = new QuotationUpdateRequest(customerB, null, false, null, null, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/quotations/" + quotationA.id(), patch, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheCreateRequestBody() {
        String tokenA = managerToken("q10-isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("q10-isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);
        UUID customerA = createCustomer(tokenA, "Org A Customer").id();
        UUID productA = createProduct(tokenA, "ISOA-SMUGGLE").id();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String bodyWithSmuggledOrgId = "{\"customerId\":\"" + customerA + "\","
                + "\"items\":[{\"productId\":\"" + productA + "\",\"quantity\":1}],"
                + "\"organizationId\":\"" + quotationB.organizationId() + "\"}";

        ResponseEntity<QuotationResponse> response = restTemplate.exchange(
                url("/api/v1/quotations"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSmuggledOrgId, headers), QuotationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotEqualTo(quotationB.organizationId());
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsQuotation() {
        String tokenA = managerToken("q10-isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("q10-isoB-query@example.com", "Iso Org B Query");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/quotations/" + quotationB.id() + "?organizationId=" + quotationB.organizationId(),
                tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsQuotation() {
        String tokenA = managerToken("q10-isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("q10-isoB-header@example.com", "Iso Org B Header");
        QuotationResponse quotationB = createQuotationForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", quotationB.organizationId().toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/quotations/" + quotationB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private QuotationResponse createQuotationForOwnOrg(String token) {
        UUID customerId = createCustomer(token, "Contact").id();
        UUID productId = createProduct(token, "SKU-" + UUID.randomUUID()).id();
        QuotationCreateRequest request = new QuotationCreateRequest(customerId, null, null,
                List.of(new QuotationItemRequest(productId, BigDecimal.ONE)));
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
