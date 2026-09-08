package com.bizpilot.analytics;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
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
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadResponse;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 19 tenant-isolation tests (project instructions §15/§33):
 * every aggregate metric is scoped to the caller's own organization — an
 * exact-value assertion, not merely "the request succeeded" (project
 * instructions §33: "Do not rely only on 'request succeeded'").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AnalyticsTenantIsolationTests {

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
    void eachOrganizationsSummaryReflectsOnlyItsOwnData() {
        String tokenA = managerToken("analytics-isoA@example.com", "Analytics Iso Org A");
        String tokenB = managerToken("analytics-isoB@example.com", "Analytics Iso Org B");

        // Org A: 2 customers, 1 qualified lead, one PAID invoice of 500.
        createCustomer(tokenA, "Org A Customer 1");
        createCustomer(tokenA, "Org A Customer 2");
        createAndTransitionLead(tokenA, "Org A Qualified Lead", LeadStatus.QUALIFIED);
        String productA = createProduct(tokenA, "SKU-A-1");
        String customerAForInvoice = createCustomer(tokenA, "Org A Invoice Customer").id().toString();
        payInvoice(tokenA, customerAForInvoice, productA, new BigDecimal("5"));

        // Org B: 5 customers, 3 qualified leads, one PAID invoice of 1000.
        for (int i = 1; i <= 5; i++) {
            createCustomer(tokenB, "Org B Customer " + i);
        }
        for (int i = 1; i <= 3; i++) {
            createAndTransitionLead(tokenB, "Org B Qualified Lead " + i, LeadStatus.QUALIFIED);
        }
        String productB = createProduct(tokenB, "SKU-B-1");
        String customerBForInvoice = createCustomer(tokenB, "Org B Invoice Customer").id().toString();
        payInvoice(tokenB, customerBForInvoice, productB, new BigDecimal("10"));

        AnalyticsSummaryResponse summaryA = getSummary(tokenA);
        AnalyticsSummaryResponse summaryB = getSummary(tokenB);

        // Org A: 2 plain customers + 1 invoice customer = 3.
        assertThat(summaryA.totalCustomers()).isEqualTo(3L);
        assertThat(summaryA.qualifiedLeads()).isEqualTo(1L);
        assertThat(summaryA.revenue()).isEqualByComparingTo("500.0000");

        // Org B: 5 plain customers + 1 invoice customer = 6.
        assertThat(summaryB.totalCustomers()).isEqualTo(6L);
        assertThat(summaryB.qualifiedLeads()).isEqualTo(3L);
        assertThat(summaryB.revenue()).isEqualByComparingTo("1000.0000");
    }

    // ---- Helpers -----------------------------------------------------------------

    private AnalyticsSummaryResponse getSummary(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<AnalyticsSummaryResponse> response = restTemplate.exchange(
                url("/api/v1/analytics/summary"), HttpMethod.GET, new HttpEntity<>(headers),
                AnalyticsSummaryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private CustomerResponse createCustomer(String token, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        CustomerCreateRequest request = new CustomerCreateRequest(name, null, null, null, null, null, null);
        ResponseEntity<CustomerResponse> response = restTemplate.postForEntity(
                url("/api/v1/customers"), new HttpEntity<>(request, headers), CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private LeadResponse createAndTransitionLead(String token, String name, LeadStatus targetStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        LeadCreateRequest createRequest = new LeadCreateRequest(name, null, null, null, LeadSource.WEBSITE, null, null);
        ResponseEntity<LeadResponse> created = restTemplate.postForEntity(
                url("/api/v1/leads"), new HttpEntity<>(createRequest, headers), LeadResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LeadResponse lead = created.getBody();
        if (targetStatus == LeadStatus.NEW) {
            return lead;
        }
        com.bizpilot.sales.dto.LeadUpdateRequest patch = new com.bizpilot.sales.dto.LeadUpdateRequest(
                null, null, null, null, targetStatus, null, null, null, false);
        ResponseEntity<LeadResponse> updated = restTemplate.exchange(
                url("/api/v1/leads/" + lead.id()), HttpMethod.PATCH, new HttpEntity<>(patch, headers),
                LeadResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        return updated.getBody();
    }

    private String createProduct(String token, String sku) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ProductCreateRequest request = new ProductCreateRequest(sku, "Product " + sku, null, "unit",
                new BigDecimal("100.00"), BigDecimal.ZERO, null);
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                url("/api/v1/products"), new HttpEntity<>(request, headers), ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id().toString();
    }

    /** Creates an invoice for {@code quantity} units of {@code productId} and transitions it straight to PAID. */
    private InvoiceResponse payInvoice(String token, String customerId, String productId, BigDecimal quantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        InvoiceCreateRequest createRequest = new InvoiceCreateRequest(
                java.util.UUID.fromString(customerId), null,
                List.of(new InvoiceItemRequest(java.util.UUID.fromString(productId), quantity)));
        ResponseEntity<InvoiceResponse> created = restTemplate.postForEntity(
                url("/api/v1/invoices"), new HttpEntity<>(createRequest, headers), InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, InvoiceStatus.PAID, null);
        ResponseEntity<InvoiceResponse> updated = restTemplate.exchange(
                url("/api/v1/invoices/" + created.getBody().id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), InvoiceResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        return updated.getBody();
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
