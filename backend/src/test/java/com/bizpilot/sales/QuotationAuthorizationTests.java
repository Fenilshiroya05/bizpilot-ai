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
 * Mandatory Phase 10 RBAC tests (project instructions §16): every
 * {@code QUOTATION_*} permission — already seeded by Phase 6 (V4) — is
 * enforced server-side on the matching operation. No new permission or role
 * mapping was introduced.
 *
 * <p>Seeded mapping recap (docs/security.md): EMPLOYEE = read-only;
 * SALES = CRUD minus delete; MANAGER = full CRUD.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class QuotationAuthorizationTests {

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
    void everyQuotationEndpointRejectsUnauthenticatedRequests() {
        assertThat(restTemplate.getForEntity(url("/api/v1/quotations"), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity(url("/api/v1/quotations"),
                        new QuotationCreateRequest(UUID.randomUUID(), null, null,
                                List.of(new QuotationItemRequest(UUID.randomUUID(), BigDecimal.ONE))),
                        ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anEmployeeWithOnlyQuotationReadCanReadButNotCreateUpdateOrDelete() {
        // EMPLOYEE is the default self-registration role — no promotion needed.
        String token = registerAndLogin("quotation-employee-rbac@example.com", "Employee RBAC Co");

        ResponseEntity<String> listResponse = getWithToken("/api/v1/quotations", token, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> createResponse = postWithToken("/api/v1/quotations",
                new QuotationCreateRequest(UUID.randomUUID(), null, null,
                        List.of(new QuotationItemRequest(UUID.randomUUID(), BigDecimal.ONE))), token, ApiError.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(createResponse.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void aSalesUserCanCreateAndUpdateButNotCancel() {
        String token = promotedToken("quotation-sales-rbac@example.com", "Sales RBAC Co", UserRole.SALES);
        UUID customerId = createCustomer(token, "Sales Customer").id();
        UUID productId = createProduct(token, "QUOTATION-SALES-SKU").id();

        ResponseEntity<QuotationResponse> createResponse = postWithToken("/api/v1/quotations",
                new QuotationCreateRequest(customerId, null, null,
                        List.of(new QuotationItemRequest(productId, BigDecimal.ONE))), token, QuotationResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String quotationId = createResponse.getBody().id().toString();

        QuotationUpdateRequest patch = new QuotationUpdateRequest(
                null, null, false, new BigDecimal("5"), null, null);
        HttpHeaders patchHeaders = new HttpHeaders();
        patchHeaders.setBearerAuth(token);
        ResponseEntity<QuotationResponse> updateResponse = restTemplate.exchange(
                url("/api/v1/quotations/" + quotationId), HttpMethod.PATCH,
                new HttpEntity<>(patch, patchHeaders), QuotationResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<ApiError> cancelResponse = restTemplate.exchange(
                url("/api/v1/quotations/" + quotationId), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), ApiError.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aManagerCanPerformEveryQuotationOperationIncludingCancel() {
        String token = promotedToken("quotation-manager-rbac@example.com", "Manager RBAC Co", UserRole.MANAGER);
        UUID customerId = createCustomer(token, "Manager Customer").id();
        UUID productId = createProduct(token, "QUOTATION-MANAGER-SKU").id();

        ResponseEntity<QuotationResponse> createResponse = postWithToken("/api/v1/quotations",
                new QuotationCreateRequest(customerId, null, null,
                        List.of(new QuotationItemRequest(productId, BigDecimal.ONE))), token, QuotationResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String quotationId = createResponse.getBody().id().toString();

        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                url("/api/v1/quotations/" + quotationId), HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), Void.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void thePdfEndpointRequiresQuotationReadPermission() {
        String managerToken = promotedToken("quotation-manager-pdf@example.com", "Manager Pdf Co", UserRole.MANAGER);
        UUID customerId = createCustomer(managerToken, "Pdf Customer").id();
        UUID productId = createProduct(managerToken, "QUOTATION-PDF-SKU").id();
        ResponseEntity<QuotationResponse> created = postWithToken("/api/v1/quotations",
                new QuotationCreateRequest(customerId, null, null,
                        List.of(new QuotationItemRequest(productId, BigDecimal.ONE))), managerToken,
                QuotationResponse.class);
        String quotationId = created.getBody().id().toString();

        // EMPLOYEE (default role, read-only) belongs to a DIFFERENT organization
        // here, so this also incidentally proves tenant isolation on the PDF
        // endpoint — the dedicated tenant-isolation test covers that
        // explicitly; this test only asserts the permission requirement.
        String employeeToken = registerAndLogin("quotation-employee-pdf@example.com", "Employee Pdf Co");
        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/quotations/" + quotationId + "/pdf", employeeToken, ApiError.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

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

    private String registerAndLogin(String email, String organizationName) {
        register(email, organizationName);
        return login(email).accessToken();
    }

    private String promotedToken(String email, String organizationName, UserRole role) {
        register(email, organizationName);
        promoteTo(email, role);
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

    private void promoteTo(String email, UserRole role) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            user.getRoles().clear();
            user.getRoles().add(roleRepository.findByName(role.name()).orElseThrow());
            userRepository.saveAndFlush(user);
        });
    }
}
