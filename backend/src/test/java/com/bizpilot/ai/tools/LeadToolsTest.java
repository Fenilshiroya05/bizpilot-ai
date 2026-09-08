package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.LeadLookupResult;
import com.bizpilot.ai.tools.dto.LeadToolResult;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.exception.LeadNotFoundException;
import com.bizpilot.sales.service.LeadSearchCriteria;
import com.bizpilot.sales.service.LeadService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadToolsTest {

    private final LeadService leadService = mock(LeadService.class);
    private final LeadTools tools = new LeadTools(leadService);

    @Test
    void searchLeadsDelegatesToLeadServiceWithAFixedPageSizeOfFive() {
        Lead lead = lead("Delta Traders");
        when(leadService.search(any(LeadSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lead)));

        List<LeadToolResult> results = tools.searchLeads("delta");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Delta Traders");
        verify(leadService).search(
                eq(new LeadSearchCriteria(null, null, null, null, false, null, false, "delta")),
                eq(PageRequest.of(0, 5)));
    }

    @Test
    void searchLeadsRejectsABlankQuery() {
        assertThatThrownBy(() -> tools.searchLeads("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLeadReturnsAFoundResultMappedFromTheEntity() {
        UUID id = UUID.randomUUID();
        Lead lead = lead("Epsilon Co");
        when(leadService.getById(id)).thenReturn(lead);

        LeadLookupResult result = tools.getLead(id);

        assertThat(result.found()).isTrue();
        assertThat(result.lead().name()).isEqualTo("Epsilon Co");
    }

    @Test
    void getLeadReturnsANotFoundResultRatherThanPropagatingTheException() {
        UUID id = UUID.randomUUID();
        when(leadService.getById(id)).thenThrow(new LeadNotFoundException(id));

        LeadLookupResult result = tools.getLead(id);

        assertThat(result.found()).isFalse();
        assertThat(result.message()).contains("No lead found");
    }

    @Test
    void getLeadRejectsANullId() {
        assertThatThrownBy(() -> tools.getLead(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Lead lead(String name) {
        Organization organization = new Organization("Test Org");
        return new Lead(organization, name, "Company", "lead@example.com", "1234567890",
                LeadSource.WEBSITE, LeadPriority.MEDIUM, null);
    }
}
