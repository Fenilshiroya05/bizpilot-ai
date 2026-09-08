package com.bizpilot.analytics;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.crm.dto.CustomerUpdateRequest;
import com.bizpilot.crm.entity.CustomerStatus;
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
import com.bizpilot.sales.dto.LeadUpdateRequest;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 19 (CLAUDE.md §22) live verification against real Testcontainers
 * Postgres data: exact metric definitions (project instructions §16-§22),
 * zero-data behavior (§38), date-boundary behavior (§40), conversion-rate
 * edge cases at the endpoint level (§39), revenue/outstanding-invoice status
 * filtering (§41/§42), pending-follow-up filtering (§43), and mutation
 * safety (§44/§34). Every assertion computes the expected value
 * independently of {@code AnalyticsService}'s own repository methods,
 * from the seeded dataset.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AnalyticsIntegrationTests {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void aTenantWithNoBusinessDataReturnsAllZeroValuesNeverNull() {
        String token = managerToken("analytics-zero@example.com", "Analytics Zero Org");

        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.totalCustomers()).isZero();
        assertThat(summary.newLeads()).isZero();
        assertThat(summary.qualifiedLeads()).isZero();
        assertThat(summary.conversionRate()).isEqualByComparingTo("0.00");
        assertThat(summary.revenue()).isEqualByComparingTo("0.00");
        assertThat(summary.outstandingInvoicesCount()).isZero();
        assertThat(summary.outstandingInvoicesTotal()).isEqualByComparingTo("0.00");
        assertThat(summary.pendingFollowUps()).isZero();
    }

    @Test
    void totalCustomersCountsOnlyNonArchivedCustomers() {
        String token = managerToken("analytics-customers@example.com", "Analytics Customers Org");
        createCustomer(token, "Active One");
        createCustomer(token, "Active Two");
        CustomerResponse inactive = createCustomer(token, "Inactive One");
        setCustomerStatus(token, inactive.id(), CustomerStatus.INACTIVE);
        CustomerResponse toArchive1 = createCustomer(token, "Archived One");
        archiveCustomer(token, toArchive1.id());
        CustomerResponse toArchive2 = createCustomer(token, "Archived Two");
        archiveCustomer(token, toArchive2.id());

        // Seeded: 5 customers, 2 archived -> expected non-archived = 3.
        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.totalCustomers()).isEqualTo(3L);
    }

    @Test
    void newLeadsCountsOnlyNonArchivedLeadsCreatedInTheLast30Days() {
        String token = managerToken("analytics-newleads@example.com", "Analytics New Leads Org");
        createLead(token, "Recent Lead 1");
        createLead(token, "Recent Lead 2");
        LeadResponse oldLead = createLead(token, "Old Lead");
        backdateLeadCreatedAt(oldLead.id(), Instant.now().minus(45, ChronoUnit.DAYS));
        LeadResponse recentArchived = createLead(token, "Recent Archived Lead");
        archiveLead(token, recentArchived.id());

        // Seeded: 4 leads total; 1 old (45 days), 1 recent-but-archived ->
        // expected newLeads = 2 (only the two plain recent, non-archived leads).
        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.newLeads()).isEqualTo(2L);
    }

    @Test
    void dateBoundaryExcludesLeadsOlderThan30DaysAndIncludesRecentOnes() {
        String token = managerToken("analytics-dateboundary@example.com", "Analytics Date Boundary Org");
        LeadResponse withinWindow = createLead(token, "Within Window");
        backdateLeadCreatedAt(withinWindow.id(), Instant.now().minus(29, ChronoUnit.DAYS));
        LeadResponse outsideWindow = createLead(token, "Outside Window");
        backdateLeadCreatedAt(outsideWindow.id(), Instant.now().minus(31, ChronoUnit.DAYS));

        AnalyticsSummaryResponse summary = getSummary(token);

        // Only the 29-day-old lead is within the last-30-days window.
        assertThat(summary.newLeads()).isEqualTo(1L);
    }

    @Test
    void qualifiedLeadsCountsOnlyCurrentQualifiedStatusRegardlessOfWhenItWasSet() {
        String token = managerToken("analytics-qualified@example.com", "Analytics Qualified Org");
        transitionLead(token, createLead(token, "Qualified 1"), LeadStatus.QUALIFIED);
        transitionLead(token, createLead(token, "Qualified 2"), LeadStatus.QUALIFIED);
        createLead(token, "Still New");
        transitionLead(token, createLead(token, "Contacted"), LeadStatus.CONTACTED);

        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.qualifiedLeads()).isEqualTo(2L);
    }

    @Test
    void conversionRateWithNoWonOrLostLeadsIsZero() {
        String token = managerToken("analytics-conv-zero@example.com", "Analytics Conv Zero Org");
        createLead(token, "Just New");
        transitionLead(token, createLead(token, "Qualified"), LeadStatus.QUALIFIED);

        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.conversionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void conversionRateWithOnlyWonLeadsIsOneHundred() {
        String token = managerToken("analytics-conv-won@example.com", "Analytics Conv Won Org");
        transitionLead(token, createLead(token, "Won 1"), LeadStatus.WON);
        transitionLead(token, createLead(token, "Won 2"), LeadStatus.WON);

        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.conversionRate()).isEqualByComparingTo("100.00");
    }

    @Test
    void conversionRateWithOnlyLostLeadsIsZero() {
        String token = managerToken("analytics-conv-lost@example.com", "Analytics Conv Lost Org");
        transitionLead(token, createLead(token, "Lost 1"), LeadStatus.LOST);

        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.conversionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void conversionRateWithMixedWonAndLostRoundsHalfUpToTwoDecimals() {
        String token = managerToken("analytics-conv-mixed@example.com", "Analytics Conv Mixed Org");
        transitionLead(token, createLead(token, "Won 1"), LeadStatus.WON);
        transitionLead(token, createLead(token, "Won 2"), LeadStatus.WON);
        transitionLead(token, createLead(token, "Lost 1"), LeadStatus.LOST);
        transitionLead(token, createLead(token, "Lost 2"), LeadStatus.LOST);
        transitionLead(token, createLead(token, "Lost 3"), LeadStatus.LOST);

        // 2 WON / (2 WON + 3 LOST) * 100 = 40.00 exactly.
        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.conversionRate()).isEqualByComparingTo("40.00");
    }

    @Test
    void revenueAndOutstandingInvoicesReflectExactlyTheLockedStatusRules() {
        String token = managerToken("analytics-invoices@example.com", "Analytics Invoices Org");
        String productId = createProduct(token, "SKU-STATUS-1");
        String customerId = createCustomer(token, "Invoice Customer").id().toString();

        // One invoice per status, distinct round amounts so each contribution is unambiguous.
        createInvoiceWithStatus(token, customerId, productId, new BigDecimal("10"), null); // DRAFT, total 1000
        createInvoiceWithStatus(token, customerId, productId, new BigDecimal("2"), InvoiceStatus.ISSUED); // 200
        createInvoiceWithStatus(token, customerId, productId, new BigDecimal("3"), InvoiceStatus.PARTIALLY_PAID); // 300
        createInvoiceWithStatus(token, customerId, productId, new BigDecimal("5"), InvoiceStatus.PAID); // 500 (revenue)
        createInvoiceWithStatus(token, customerId, productId, new BigDecimal("4"), InvoiceStatus.OVERDUE); // 400
        createInvoiceWithStatus(token, customerId, productId, new BigDecimal("1"), InvoiceStatus.CANCELLED); // 100

        AnalyticsSummaryResponse summary = getSummary(token);

        // Revenue = only the PAID invoice's total.
        assertThat(summary.revenue()).isEqualByComparingTo("500.0000");
        // Outstanding = ISSUED (200) + PARTIALLY_PAID (300) + OVERDUE (400).
        assertThat(summary.outstandingInvoicesCount()).isEqualTo(3L);
        assertThat(summary.outstandingInvoicesTotal()).isEqualByComparingTo("900.0000");
    }

    @Test
    void revenueExcludesAPaidInvoiceCreatedOutsideTheThirtyDayWindow() {
        String token = managerToken("analytics-revenue-window@example.com", "Analytics Revenue Window Org");
        String productId = createProduct(token, "SKU-WINDOW-1");
        String customerId = createCustomer(token, "Window Customer").id().toString();

        InvoiceResponse recentPaid = createInvoiceWithStatus(token, customerId, productId,
                new BigDecimal("6"), InvoiceStatus.PAID); // 600, recent -> included
        InvoiceResponse oldPaid = createInvoiceWithStatus(token, customerId, productId,
                new BigDecimal("9"), InvoiceStatus.PAID); // 900, will be backdated -> excluded
        backdateInvoiceCreatedAt(oldPaid.id(), Instant.now().minus(45, ChronoUnit.DAYS));

        AnalyticsSummaryResponse summary = getSummary(token);

        assertThat(summary.revenue()).isEqualByComparingTo("600.0000");
    }

    @Test
    void pendingFollowUpsIncludesOnlyDueNonWonLostNonArchivedLeadsExcludingTaskDueDates() {
        String token = managerToken("analytics-followups@example.com", "Analytics Follow-ups Org");
        LocalDate today = LocalDate.now();

        LeadResponse pastDue = createLeadWithFollowUp(token, "Past Due", today.minusDays(5));
        LeadResponse dueToday = createLeadWithFollowUp(token, "Due Today", today);
        createLeadWithFollowUp(token, "Future", today.plusDays(10));
        createLead(token, "No Follow-up Date");

        LeadResponse wonWithDueDate = createLeadWithFollowUp(token, "Won But Due", today.minusDays(1));
        transitionLead(token, wonWithDueDate, LeadStatus.WON);

        LeadResponse archivedWithDueDate = createLeadWithFollowUp(token, "Archived But Due", today.minusDays(1));
        archiveLead(token, archivedWithDueDate.id());

        // Sanity: pastDue/dueToday remain in an open, non-WON/LOST status.
        assertThat(pastDue.status()).isEqualTo(LeadStatus.NEW);
        assertThat(dueToday.status()).isEqualTo(LeadStatus.NEW);

        AnalyticsSummaryResponse summary = getSummary(token);

        // Only "Past Due" and "Due Today" qualify.
        assertThat(summary.pendingFollowUps()).isEqualTo(2L);
    }

    @Test
    void summaryRequestNeverMutatesAnyBusinessRecord() {
        String token = managerToken("analytics-readonly@example.com", "Analytics Read-Only Org");
        CustomerResponse customer = createCustomer(token, "Untouched Customer");
        LeadResponse lead = createLead(token, "Untouched Lead");
        String productId = createProduct(token, "SKU-READONLY-1");
        InvoiceResponse invoice = createInvoiceWithStatus(token, customer.id().toString(), productId,
                new BigDecimal("2"), InvoiceStatus.ISSUED);

        // Call the summary endpoint (and a second time, for good measure).
        getSummary(token);
        getSummary(token);

        CustomerResponse reloadedCustomer = getCustomer(token, customer.id());
        LeadResponse reloadedLead = getLead(token, lead.id());
        InvoiceResponse reloadedInvoice = getInvoice(token, invoice.id());

        assertThat(reloadedCustomer.status()).isEqualTo(customer.status());
        assertThat(reloadedCustomer.updatedAt()).isEqualTo(customer.updatedAt());
        assertThat(reloadedLead.status()).isEqualTo(lead.status());
        assertThat(reloadedLead.updatedAt()).isEqualTo(lead.updatedAt());
        assertThat(reloadedInvoice.status()).isEqualTo(invoice.status());
        assertThat(reloadedInvoice.total()).isEqualByComparingTo(invoice.total());
        assertThat(reloadedInvoice.updatedAt()).isEqualTo(invoice.updatedAt());
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

    private CustomerResponse getCustomer(String token, UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<CustomerResponse> response = restTemplate.exchange(
                url("/api/v1/customers/" + id), HttpMethod.GET, new HttpEntity<>(headers), CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void setCustomerStatus(String token, UUID id, CustomerStatus status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        CustomerUpdateRequest patch = new CustomerUpdateRequest(null, null, null, null, null, null, null, status);
        ResponseEntity<CustomerResponse> response = restTemplate.exchange(
                url("/api/v1/customers/" + id), HttpMethod.PATCH, new HttpEntity<>(patch, headers),
                CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void archiveCustomer(String token, UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/v1/customers/" + id), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private LeadResponse createLead(String token, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        LeadCreateRequest request = new LeadCreateRequest(name, null, null, null, LeadSource.WEBSITE, null, null);
        ResponseEntity<LeadResponse> response = restTemplate.postForEntity(
                url("/api/v1/leads"), new HttpEntity<>(request, headers), LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private LeadResponse createLeadWithFollowUp(String token, String name, LocalDate followUpDate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        LeadCreateRequest request = new LeadCreateRequest(name, null, null, null, LeadSource.WEBSITE, null,
                followUpDate);
        ResponseEntity<LeadResponse> response = restTemplate.postForEntity(
                url("/api/v1/leads"), new HttpEntity<>(request, headers), LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private LeadResponse getLead(String token, UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<LeadResponse> response = restTemplate.exchange(
                url("/api/v1/leads/" + id), HttpMethod.GET, new HttpEntity<>(headers), LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private LeadResponse transitionLead(String token, LeadResponse lead, LeadStatus targetStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        LeadUpdateRequest patch = new LeadUpdateRequest(null, null, null, null, targetStatus, null, null, null, false);
        ResponseEntity<LeadResponse> response = restTemplate.exchange(
                url("/api/v1/leads/" + lead.id()), HttpMethod.PATCH, new HttpEntity<>(patch, headers),
                LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void archiveLead(String token, UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/v1/leads/" + id), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

    /**
     * Creates an invoice (product priced at 100.00/unit, 0% tax, so total =
     * {@code quantity * 100}) and, unless {@code targetStatus} is {@code
     * null} (leaving it DRAFT), transitions it to {@code targetStatus} in
     * one PATCH — permitted since the invoice is still DRAFT at that point
     * (see {@code InvoiceService.update}'s Javadoc: a DRAFT invoice may move
     * directly to any non-CANCELLED status in a single update). {@code
     * CANCELLED} instead uses the dedicated cancel endpoint.
     */
    private InvoiceResponse createInvoiceWithStatus(String token, String customerId, String productId,
                                                      BigDecimal quantity, InvoiceStatus targetStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        InvoiceCreateRequest createRequest = new InvoiceCreateRequest(
                UUID.fromString(customerId), null, List.of(new InvoiceItemRequest(UUID.fromString(productId), quantity)));
        ResponseEntity<InvoiceResponse> created = restTemplate.postForEntity(
                url("/api/v1/invoices"), new HttpEntity<>(createRequest, headers), InvoiceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        InvoiceResponse invoice = created.getBody();

        if (targetStatus == null) {
            return invoice;
        }
        if (targetStatus == InvoiceStatus.CANCELLED) {
            ResponseEntity<Void> cancelled = restTemplate.exchange(
                    url("/api/v1/invoices/" + invoice.id()), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            return getInvoice(token, invoice.id());
        }
        InvoiceUpdateRequest patch = new InvoiceUpdateRequest(null, null, false, targetStatus, null);
        ResponseEntity<InvoiceResponse> updated = restTemplate.exchange(
                url("/api/v1/invoices/" + invoice.id()), HttpMethod.PATCH, new HttpEntity<>(patch, headers),
                InvoiceResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        return updated.getBody();
    }

    private InvoiceResponse getInvoice(String token, UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<InvoiceResponse> response = restTemplate.exchange(
                url("/api/v1/invoices/" + id), HttpMethod.GET, new HttpEntity<>(headers), InvoiceResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /**
     * Test-only backdating: {@code created_at} is {@code @CreatedDate}-
     * managed and always set to "now" at first persist, with no
     * application-level way to override it — a direct SQL update against
     * the already-created row is the minimal way to make the 30-day
     * boundary deterministic for a test, without introducing any {@code
     * Clock} abstraction into production code (project instructions §29:
     * "do not add unnecessary infrastructure"; none exists anywhere else in
     * this codebase today).
     */
    private void backdateLeadCreatedAt(UUID leadId, Instant createdAt) {
        jdbcTemplate.update("UPDATE leads SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), leadId);
    }

    private void backdateInvoiceCreatedAt(UUID invoiceId, Instant createdAt) {
        jdbcTemplate.update("UPDATE invoices SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), invoiceId);
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
