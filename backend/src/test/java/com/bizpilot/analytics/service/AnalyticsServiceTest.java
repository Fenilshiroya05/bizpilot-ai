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
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.sales.entity.QuotationStatus;
import com.bizpilot.sales.repository.CustomerRevenueRow;
import com.bizpilot.sales.repository.InvoiceRepository;
import com.bizpilot.sales.repository.InvoiceRevenuePoint;
import com.bizpilot.sales.repository.LeadRepository;
import com.bizpilot.sales.repository.LeadSourceCount;
import com.bizpilot.sales.repository.QuotationPipelineRow;
import com.bizpilot.sales.repository.QuotationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests — no Spring context, no database. Live aggregate-
 * query correctness against real Postgres is verified in {@code
 * AnalyticsIntegrationTests}; this class only proves correct repository
 * delegation, mapping, and the conversion-rate/null-handling arithmetic.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private TenantContext tenantContext;

    private AnalyticsService service() {
        return new AnalyticsService(customerRepository, leadRepository, invoiceRepository, quotationRepository,
                tenantContext);
    }

    private static LeadSourceCount leadSourceCount(LeadSource source, long count) {
        LeadSourceCount row = mock(LeadSourceCount.class);
        when(row.getSource()).thenReturn(source);
        when(row.getCount()).thenReturn(count);
        return row;
    }

    private static QuotationPipelineRow pipelineRow(QuotationStatus status, long count, BigDecimal amount) {
        QuotationPipelineRow row = mock(QuotationPipelineRow.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getCount()).thenReturn(count);
        when(row.getAmount()).thenReturn(amount);
        return row;
    }

    private static CustomerRevenueRow customerRevenueRow(UUID customerId, String name, BigDecimal revenue) {
        CustomerRevenueRow row = mock(CustomerRevenueRow.class);
        when(row.getCustomerId()).thenReturn(customerId);
        when(row.getCustomerName()).thenReturn(name);
        when(row.getRevenue()).thenReturn(revenue);
        return row;
    }

    private static InvoiceRevenuePoint revenuePoint(Instant createdAt, BigDecimal total) {
        InvoiceRevenuePoint point = mock(InvoiceRevenuePoint.class);
        when(point.getCreatedAt()).thenReturn(createdAt);
        when(point.getTotal()).thenReturn(total);
        return point;
    }

    private void stubOrganization(UUID organizationId) {
        when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
    }

    private void stubZeroData() {
        when(customerRepository.countExcludingArchived(any())).thenReturn(0L);
        when(leadRepository.countCreatedOnOrAfterExcludingArchived(any(), any())).thenReturn(0L);
        when(leadRepository.countByStatus(any(), any())).thenReturn(0L);
        when(leadRepository.countPendingFollowUpsExcludingArchived(any(), any())).thenReturn(0L);
        when(invoiceRepository.sumPaidTotalCreatedOnOrAfter(any(), any())).thenReturn(null);
        when(invoiceRepository.countByStatuses(any(), anyList())).thenReturn(0L);
        when(invoiceRepository.sumTotalByStatuses(any(), anyList())).thenReturn(null);
    }

    @Test
    void resolvesTheOrganizationFromTenantContextAndPassesItToEveryRepositoryCall() {
        UUID organizationId = UUID.randomUUID();
        stubOrganization(organizationId);
        stubZeroData();

        service().getSummary();

        verify(customerRepository).countExcludingArchived(organizationId);
        verify(leadRepository).countCreatedOnOrAfterExcludingArchived(eq(organizationId), any());
        verify(leadRepository, org.mockito.Mockito.times(3)).countByStatus(eq(organizationId), any());
        verify(leadRepository).countPendingFollowUpsExcludingArchived(eq(organizationId), any());
        verify(invoiceRepository).sumPaidTotalCreatedOnOrAfter(eq(organizationId), any());
        verify(invoiceRepository).countByStatuses(eq(organizationId), anyList());
        verify(invoiceRepository).sumTotalByStatuses(eq(organizationId), anyList());
    }

    @Test
    void queriesQualifiedWonAndLostLeadStatusesSeparately() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();

        service().getSummary();

        verify(leadRepository).countByStatus(any(), eq(LeadStatus.QUALIFIED));
        verify(leadRepository).countByStatus(any(), eq(LeadStatus.WON));
        verify(leadRepository).countByStatus(any(), eq(LeadStatus.LOST));
    }

    @Test
    void queriesOutstandingInvoicesUsingExactlyTheThreeLockedStatuses() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        ArgumentCaptor<List<InvoiceStatus>> captor = ArgumentCaptor.forClass(List.class);

        service().getSummary();

        verify(invoiceRepository).countByStatuses(any(), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);
    }

    @Test
    void usesA30DayCutoffForNewLeadsAndRevenue() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        ArgumentCaptor<Instant> leadsCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> revenueCutoff = ArgumentCaptor.forClass(Instant.class);

        service().getSummary();

        verify(leadRepository).countCreatedOnOrAfterExcludingArchived(any(), leadsCutoff.capture());
        verify(invoiceRepository).sumPaidTotalCreatedOnOrAfter(any(), revenueCutoff.capture());
        Instant expected = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(leadsCutoff.getValue()).isCloseTo(expected, org.assertj.core.api.Assertions.within(5,
                ChronoUnit.SECONDS));
        assertThat(revenueCutoff.getValue()).isCloseTo(expected, org.assertj.core.api.Assertions.within(5,
                ChronoUnit.SECONDS));
    }

    @Test
    void usesTodayAsThePendingFollowUpBoundary() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);

        service().getSummary();

        verify(leadRepository).countPendingFollowUpsExcludingArchived(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void mapsEveryRepositoryResultDirectlyIntoTheResponse() {
        stubOrganization(UUID.randomUUID());
        when(customerRepository.countExcludingArchived(any())).thenReturn(7L);
        when(leadRepository.countCreatedOnOrAfterExcludingArchived(any(), any())).thenReturn(8L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.QUALIFIED))).thenReturn(1L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.WON))).thenReturn(2L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.LOST))).thenReturn(3L);
        when(leadRepository.countPendingFollowUpsExcludingArchived(any(), any())).thenReturn(4L);
        when(invoiceRepository.sumPaidTotalCreatedOnOrAfter(any(), any())).thenReturn(new BigDecimal("1000.0000"));
        when(invoiceRepository.countByStatuses(any(), anyList())).thenReturn(3L);
        when(invoiceRepository.sumTotalByStatuses(any(), anyList())).thenReturn(new BigDecimal("650.0000"));

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.totalCustomers()).isEqualTo(7L);
        assertThat(response.newLeads()).isEqualTo(8L);
        assertThat(response.qualifiedLeads()).isEqualTo(1L);
        assertThat(response.conversionRate()).isEqualByComparingTo("40.00");
        assertThat(response.revenue()).isEqualByComparingTo("1000.0000");
        assertThat(response.outstandingInvoicesCount()).isEqualTo(3L);
        assertThat(response.outstandingInvoicesTotal()).isEqualByComparingTo("650.0000");
        assertThat(response.pendingFollowUps()).isEqualTo(4L);
    }

    @Test
    void zeroDataReturnsAllZeroValuesNeverNull() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.totalCustomers()).isZero();
        assertThat(response.newLeads()).isZero();
        assertThat(response.qualifiedLeads()).isZero();
        assertThat(response.conversionRate()).isEqualByComparingTo("0.00");
        assertThat(response.revenue()).isEqualByComparingTo("0.0000");
        assertThat(response.outstandingInvoicesCount()).isZero();
        assertThat(response.outstandingInvoicesTotal()).isEqualByComparingTo("0.0000");
        assertThat(response.pendingFollowUps()).isZero();
    }

    @Test
    void conversionRateWithNoWonOrLostIsZero() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.conversionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void conversionRateWithOnlyWonIsOneHundred() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        when(leadRepository.countByStatus(any(), eq(LeadStatus.WON))).thenReturn(5L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.LOST))).thenReturn(0L);

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.conversionRate()).isEqualByComparingTo("100.00");
    }

    @Test
    void conversionRateWithOnlyLostIsZero() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        when(leadRepository.countByStatus(any(), eq(LeadStatus.WON))).thenReturn(0L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.LOST))).thenReturn(4L);

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.conversionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void conversionRateRoundsHalfUpToTwoDecimalPlaces() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        // 2 / (2 + 3) * 100 = 40.00 exactly.
        when(leadRepository.countByStatus(any(), eq(LeadStatus.WON))).thenReturn(2L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.LOST))).thenReturn(3L);

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.conversionRate()).isEqualByComparingTo("40.00");
    }

    @Test
    void conversionRateHandlesARepeatingDecimalWithHalfUpRounding() {
        stubOrganization(UUID.randomUUID());
        stubZeroData();
        // 2 / (2 + 5) * 100 = 28.5714... -> 28.57
        when(leadRepository.countByStatus(any(), eq(LeadStatus.WON))).thenReturn(2L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.LOST))).thenReturn(5L);

        AnalyticsSummaryResponse response = service().getSummary();

        assertThat(response.conversionRate()).isEqualByComparingTo("28.57");
    }

    // ---- Phase 26 chart/widget aggregates --------------------------------------------------

    @Test
    void revenueTrendReturnsExactlyThirtyDaysEndingTodayEvenWithNoInvoices() {
        stubOrganization(UUID.randomUUID());
        when(invoiceRepository.findPaidRevenuePointsCreatedOnOrAfter(any(), any())).thenReturn(List.of());

        List<RevenueTrendPointResponse> trend = service().getRevenueTrend();

        assertThat(trend).hasSize(30);
        assertThat(trend.get(0).period()).isEqualTo(LocalDate.now().minusDays(29));
        assertThat(trend.get(29).period()).isEqualTo(LocalDate.now());
        assertThat(trend).allSatisfy(point -> assertThat(point.revenue()).isEqualByComparingTo("0.0000"));
    }

    @Test
    void revenueTrendSumsMultipleInvoicesOnTheSameDayIntoOnePoint() {
        stubOrganization(UUID.randomUUID());
        Instant today = Instant.now();
        List<InvoiceRevenuePoint> points = List.of(
                revenuePoint(today, new BigDecimal("100.0000")),
                revenuePoint(today, new BigDecimal("50.0000")));
        when(invoiceRepository.findPaidRevenuePointsCreatedOnOrAfter(any(), any())).thenReturn(points);

        List<RevenueTrendPointResponse> trend = service().getRevenueTrend();

        RevenueTrendPointResponse todayPoint = trend.get(trend.size() - 1);
        assertThat(todayPoint.period()).isEqualTo(LocalDate.now());
        assertThat(todayPoint.revenue()).isEqualByComparingTo("150.0000");
    }

    @Test
    void leadFunnelQueriesEveryLeadStatusExactlyOnceAndMapsInEnumOrder() {
        stubOrganization(UUID.randomUUID());
        when(leadRepository.countByStatus(any(), any())).thenReturn(0L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.NEW))).thenReturn(5L);
        when(leadRepository.countByStatus(any(), eq(LeadStatus.WON))).thenReturn(2L);

        List<LeadFunnelStageResponse> funnel = service().getLeadFunnel();

        assertThat(funnel).hasSize(LeadStatus.values().length);
        assertThat(funnel).extracting(LeadFunnelStageResponse::status)
                .containsExactly(LeadStatus.values());
        assertThat(funnel.get(0).count()).isEqualTo(5L);
        verify(leadRepository, times(LeadStatus.values().length)).countByStatus(any(), any());
    }

    @Test
    void leadSourcesMapsEachGroupedRowDirectly() {
        stubOrganization(UUID.randomUUID());
        List<LeadSourceCount> rows = List.of(
                leadSourceCount(LeadSource.WEBSITE, 4L),
                leadSourceCount(LeadSource.REFERRAL, 1L));
        when(leadRepository.countGroupedBySource(any())).thenReturn(rows);

        List<LeadSourceBreakdownResponse> sources = service().getLeadSources();

        assertThat(sources).containsExactly(
                new LeadSourceBreakdownResponse(LeadSource.WEBSITE, 4L),
                new LeadSourceBreakdownResponse(LeadSource.REFERRAL, 1L));
    }

    @Test
    void leadSourcesReturnsAnEmptyListWithNoSyntheticZeroRows() {
        stubOrganization(UUID.randomUUID());
        when(leadRepository.countGroupedBySource(any())).thenReturn(List.of());

        assertThat(service().getLeadSources()).isEmpty();
    }

    @Test
    void salesPipelineMapsEachGroupedRowDirectlyWithoutNullHandling() {
        stubOrganization(UUID.randomUUID());
        List<QuotationPipelineRow> rows = List.of(
                pipelineRow(QuotationStatus.DRAFT, 2L, new BigDecimal("500.0000")),
                pipelineRow(QuotationStatus.ACCEPTED, 1L, new BigDecimal("1200.0000")));
        when(quotationRepository.countAndSumGroupedByStatus(any())).thenReturn(rows);

        List<SalesPipelineStageResponse> pipeline = service().getSalesPipeline();

        assertThat(pipeline).containsExactly(
                new SalesPipelineStageResponse(QuotationStatus.DRAFT, 2L, new BigDecimal("500.0000")),
                new SalesPipelineStageResponse(QuotationStatus.ACCEPTED, 1L, new BigDecimal("1200.0000")));
    }

    @Test
    void topCustomersRequestsExactlyTheLockedTopNAsAnUnsortedPageable() {
        stubOrganization(UUID.randomUUID());
        when(invoiceRepository.findTopCustomersByRevenue(any(), any())).thenReturn(List.of());
        org.springframework.data.domain.Pageable expected =
                org.springframework.data.domain.PageRequest.of(0, 5);

        service().getTopCustomers();

        verify(invoiceRepository).findTopCustomersByRevenue(any(), eq(expected));
    }

    @Test
    void topCustomersMapsEachRowDirectlyInDescendingOrder() {
        stubOrganization(UUID.randomUUID());
        UUID customerId = UUID.randomUUID();
        List<CustomerRevenueRow> rows =
                List.of(customerRevenueRow(customerId, "Top Customer", new BigDecimal("9999.0000")));
        when(invoiceRepository.findTopCustomersByRevenue(any(), any())).thenReturn(rows);

        List<TopCustomerResponse> topCustomers = service().getTopCustomers();

        assertThat(topCustomers).containsExactly(
                new TopCustomerResponse(customerId, "Top Customer", new BigDecimal("9999.0000")));
    }
}
