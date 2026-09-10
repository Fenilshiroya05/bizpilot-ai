package com.bizpilot.analytics.service;

import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
import com.bizpilot.analytics.dto.LeadFunnelStageResponse;
import com.bizpilot.analytics.dto.LeadSourceBreakdownResponse;
import com.bizpilot.analytics.dto.RevenueTrendPointResponse;
import com.bizpilot.analytics.dto.SalesPipelineStageResponse;
import com.bizpilot.analytics.dto.TopCustomerResponse;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.sales.repository.InvoiceRepository;
import com.bizpilot.sales.repository.InvoiceRevenuePoint;
import com.bizpilot.sales.repository.LeadRepository;
import com.bizpilot.sales.repository.QuotationRepository;
import com.bizpilot.sales.service.InvoiceCalculator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Computes the Phase 19 business summary (CLAUDE.md §22) — every number is
 * a fresh, database-computed aggregate over the caller's own tenant data;
 * nothing here is cached, persisted, or estimated.
 *
 * <p><b>Deliberate architectural exception (project instructions §14,
 * documented in {@code docs/architecture.md}):</b> this service calls
 * {@link CustomerRepository}/{@link LeadRepository}/{@link
 * InvoiceRepository} directly rather than through {@code CustomerService}/
 * {@code LeadService}/{@code InvoiceService}. Every other AI/cross-module
 * caller in this codebase (Phase 17 tools, Phase 18 scoring) goes through
 * the domain service specifically to reuse that service's business rules
 * for an operation *on a specific record*. A cross-cutting {@code COUNT}/
 * {@code SUM} has no equivalent single-record business rule to preserve —
 * there is nothing in {@code LeadService}, for example, that this class
 * would otherwise be bypassing, so adding six narrow, reporting-only
 * methods to unrelated domain services purely to satisfy a "go through the
 * service" rule would be a pure abstraction with no behavioral purpose.
 *
 * <p><b>Tenant isolation</b> is enforced the same way as every other
 * tenant-scoped read in this codebase: {@link TenantContext#currentOrganizationId()}
 * resolves the organization from the authenticated request context — this
 * class never accepts an organization id as a parameter from any caller.
 *
 * <p><b>Read-only.</b> No method here calls any repository's {@code save}/
 * {@code delete}/{@code deleteBy...} method, and the whole computation runs
 * inside a single {@code readOnly} transaction.
 *
 * <p><b>No AI.</b> Every value here — including the Phase 26 chart/widget
 * aggregates below — is computed deterministically via SQL aggregation: no
 * {@code AiChatService}, no prompt, no model call of any kind, anywhere in
 * this class. CLAUDE.md's "AI Business Insights" panel is built entirely in
 * the frontend as data-grounded sentences generated from these same
 * deterministic responses — it never calls an LLM either.
 */
@Service
public class AnalyticsService {

    /**
     * Locked (project instructions §17/§20): fixed at 30 days, never a
     * client-supplied period — no {@code ?since=}/{@code ?days=} parameter
     * exists on the summary endpoint. Reused as-is for the Phase 26 revenue
     * trend window.
     */
    private static final int RECENT_WINDOW_DAYS = 30;

    /** Locked (project instructions §21): the three "still owed" statuses. */
    private static final List<InvoiceStatus> OUTSTANDING_STATUSES =
            List.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    /** Locked (project instructions §31): 2 decimal places, HALF_UP. */
    private static final int CONVERSION_RATE_SCALE = 2;
    private static final RoundingMode CONVERSION_RATE_ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Phase 26 (CLAUDE.md §22 "top customers" widget): a bounded top-N,
     * never the full customer list — small enough for a dashboard card.
     */
    private static final int TOP_CUSTOMERS_LIMIT = 5;

    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final InvoiceRepository invoiceRepository;
    private final QuotationRepository quotationRepository;
    private final TenantContext tenantContext;

    public AnalyticsService(CustomerRepository customerRepository, LeadRepository leadRepository,
                             InvoiceRepository invoiceRepository, QuotationRepository quotationRepository,
                             TenantContext tenantContext) {
        this.customerRepository = customerRepository;
        this.leadRepository = leadRepository;
        this.invoiceRepository = invoiceRepository;
        this.quotationRepository = quotationRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary() {
        UUID organizationId = tenantContext.currentOrganizationId();
        Instant recentSince = Instant.now().minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS);
        LocalDate today = LocalDate.now();

        long totalCustomers = customerRepository.countExcludingArchived(organizationId);
        long newLeads = leadRepository.countCreatedOnOrAfterExcludingArchived(organizationId, recentSince);
        long qualifiedLeads = leadRepository.countByStatus(organizationId, LeadStatus.QUALIFIED);
        long won = leadRepository.countByStatus(organizationId, LeadStatus.WON);
        long lost = leadRepository.countByStatus(organizationId, LeadStatus.LOST);
        BigDecimal conversionRate = calculateConversionRate(won, lost);

        BigDecimal revenue = orZero(invoiceRepository.sumPaidTotalCreatedOnOrAfter(organizationId, recentSince));
        long outstandingInvoicesCount = invoiceRepository.countByStatuses(organizationId, OUTSTANDING_STATUSES);
        // Documented limitation (project instructions §21): Invoice has no
        // separate remaining-balance field — `total` is the only monetary
        // amount this entity carries. For a PARTIALLY_PAID invoice, this
        // sum reflects its full original total, not the true unpaid
        // remainder, since no such field exists to compute from.
        BigDecimal outstandingInvoicesTotal = orZero(
                invoiceRepository.sumTotalByStatuses(organizationId, OUTSTANDING_STATUSES));

        long pendingFollowUps = leadRepository.countPendingFollowUpsExcludingArchived(organizationId, today);

        return new AnalyticsSummaryResponse(totalCustomers, newLeads, qualifiedLeads, conversionRate, revenue,
                outstandingInvoicesCount, outstandingInvoicesTotal, pendingFollowUps);
    }

    /**
     * Phase 26 (CLAUDE.md §22 "revenue trend" chart): one point per calendar
     * day in the {@link #RECENT_WINDOW_DAYS}-day window, every day present
     * even with zero revenue (so the chart's X axis is continuous), each
     * day's value the sum of {@code total} for PAID invoices created that
     * day — the same "revenue" definition as {@link #getSummary}. Bucketing
     * happens here, in Java, from the already date-ordered rows returned by
     * {@link InvoiceRepository#findPaidRevenuePointsCreatedOnOrAfter} —
     * never a database-specific date-truncation function.
     */
    @Transactional(readOnly = true)
    public List<RevenueTrendPointResponse> getRevenueTrend() {
        UUID organizationId = tenantContext.currentOrganizationId();
        Instant since = Instant.now().minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS);
        List<InvoiceRevenuePoint> points =
                invoiceRepository.findPaidRevenuePointsCreatedOnOrAfter(organizationId, since);

        Map<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        LocalDate windowStart = LocalDate.now().minusDays(RECENT_WINDOW_DAYS - 1L);
        for (long offset = 0; offset < RECENT_WINDOW_DAYS; offset++) {
            revenueByDay.put(windowStart.plusDays(offset), orZero(null));
        }
        for (InvoiceRevenuePoint point : points) {
            LocalDate day = LocalDate.ofInstant(point.getCreatedAt(), ZoneId.systemDefault());
            revenueByDay.merge(day, point.getTotal(), BigDecimal::add);
        }

        return revenueByDay.entrySet().stream()
                .map(entry -> new RevenueTrendPointResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Phase 26 (CLAUDE.md §22 "lead funnel" chart): the actual current
     * {@link LeadStatus} distribution — reuses {@link
     * LeadRepository#countByStatus} (the same method {@link #getSummary}
     * already calls three times) for all 7 statuses, so every entry is one
     * more use of an already-proven query rather than a new aggregate.
     */
    @Transactional(readOnly = true)
    public List<LeadFunnelStageResponse> getLeadFunnel() {
        UUID organizationId = tenantContext.currentOrganizationId();
        return Arrays.stream(LeadStatus.values())
                .map(status -> new LeadFunnelStageResponse(status, leadRepository.countByStatus(organizationId, status)))
                .toList();
    }

    /**
     * Phase 26 (CLAUDE.md §22 "lead sources" chart): grouped lead count per
     * {@code source}, straight from {@link LeadRepository#countGroupedBySource}.
     */
    @Transactional(readOnly = true)
    public List<LeadSourceBreakdownResponse> getLeadSources() {
        UUID organizationId = tenantContext.currentOrganizationId();
        return leadRepository.countGroupedBySource(organizationId).stream()
                .map(row -> new LeadSourceBreakdownResponse(row.getSource(), row.getCount()))
                .toList();
    }

    /**
     * Phase 26 (CLAUDE.md §22 "sales pipeline" chart): grouped quotation
     * count/amount per {@code status}, straight from {@link
     * QuotationRepository#countAndSumGroupedByStatus}. {@code amount} can
     * never be {@code null} here (unlike {@link #getSummary}'s invoice
     * sums): every row comes from a {@code GROUP BY} over at least one real
     * quotation, and {@code grandTotal} itself is never null.
     */
    @Transactional(readOnly = true)
    public List<SalesPipelineStageResponse> getSalesPipeline() {
        UUID organizationId = tenantContext.currentOrganizationId();
        return quotationRepository.countAndSumGroupedByStatus(organizationId).stream()
                .map(row -> new SalesPipelineStageResponse(row.getStatus(), row.getCount(), row.getAmount()))
                .toList();
    }

    /**
     * Phase 26 (CLAUDE.md §22 "top customers" widget): the top {@link
     * #TOP_CUSTOMERS_LIMIT} customers by all-time PAID-invoice revenue,
     * straight from {@link InvoiceRepository#findTopCustomersByRevenue}.
     */
    @Transactional(readOnly = true)
    public List<TopCustomerResponse> getTopCustomers() {
        UUID organizationId = tenantContext.currentOrganizationId();
        return invoiceRepository.findTopCustomersByRevenue(organizationId, PageRequest.of(0, TOP_CUSTOMERS_LIMIT))
                .stream()
                .map(row -> new TopCustomerResponse(row.getCustomerId(), row.getCustomerName(), row.getRevenue()))
                .toList();
    }

    /**
     * Locked (project instructions §19): {@code WON / (WON + LOST) * 100},
     * excluding every open status from the denominator entirely. A zero
     * denominator returns {@code 0.00} — never {@code NaN}/{@code
     * Infinity}/{@code null}.
     */
    private static BigDecimal calculateConversionRate(long won, long lost) {
        long denominator = won + lost;
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(CONVERSION_RATE_SCALE, CONVERSION_RATE_ROUNDING);
        }
        return BigDecimal.valueOf(won)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), CONVERSION_RATE_SCALE, CONVERSION_RATE_ROUNDING);
    }

    /**
     * JPQL {@code SUM} over zero rows returns {@code null}, not {@code 0} —
     * standard aggregate-query semantics, deliberately not hidden behind a
     * {@code COALESCE} in the query itself (an unverified assumption about
     * numeric-literal type coercion across JPQL/Hibernate versions). This
     * is the one place that {@code null} is turned into a zero value,
     * scaled to match the existing {@code NUMERIC(19,4)} monetary
     * convention ({@link InvoiceCalculator#MONEY_SCALE}/{@link
     * InvoiceCalculator#ROUNDING_MODE}, reused rather than duplicated).
     */
    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value
                : BigDecimal.ZERO.setScale(InvoiceCalculator.MONEY_SCALE, InvoiceCalculator.ROUNDING_MODE);
    }
}
