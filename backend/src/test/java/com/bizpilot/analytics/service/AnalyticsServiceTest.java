package com.bizpilot.analytics.service;

import com.bizpilot.analytics.dto.AnalyticsSummaryResponse;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.sales.repository.InvoiceRepository;
import com.bizpilot.sales.repository.LeadRepository;
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
    private TenantContext tenantContext;

    private AnalyticsService service() {
        return new AnalyticsService(customerRepository, leadRepository, invoiceRepository, tenantContext);
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
}
