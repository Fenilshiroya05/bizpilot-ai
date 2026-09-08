package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.CustomerActivityToolResult;
import com.bizpilot.ai.tools.dto.CustomerHistoryResult;
import com.bizpilot.ai.tools.dto.CustomerLookupResult;
import com.bizpilot.ai.tools.dto.CustomerToolResult;
import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.entity.CustomerActivity;
import com.bizpilot.crm.entity.CustomerActivityType;
import com.bizpilot.crm.entity.CustomerStatus;
import com.bizpilot.crm.exception.CustomerNotFoundException;
import com.bizpilot.crm.service.CustomerService;
import com.bizpilot.organization.entity.Organization;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests — no Spring context, no database. Tenant
 * isolation/permission-enforcement live-verification is in {@code
 * AiToolsIntegrationTests}; this class only proves correct delegation,
 * mapping, validation, and not-found handling.
 */
class CustomerToolsTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerTools tools = new CustomerTools(customerService);

    @Test
    void searchCustomersDelegatesToCustomerServiceWithAFixedPageSizeOfFive() {
        Customer customer = customer("Acme Corp");
        when(customerService.search(isNull(), eq("acme"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        List<CustomerToolResult> results = tools.searchCustomers("acme");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Acme Corp");
        verify(customerService).search(isNull(), eq("acme"), eq(PageRequest.of(0, 5)));
    }

    @Test
    void searchCustomersRejectsABlankQuery() {
        assertThatThrownBy(() -> tools.searchCustomers("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchCustomersTruncatesAnExcessivelyLongQuery() {
        String tooLong = "a".repeat(500);
        when(customerService.search(isNull(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        tools.searchCustomers(tooLong);

        verify(customerService).search(isNull(), eq("a".repeat(200)), any(Pageable.class));
    }

    @Test
    void getCustomerReturnsAFoundResultMappedFromTheEntity() {
        UUID id = UUID.randomUUID();
        Customer customer = customer("Beta Industries");
        when(customerService.getById(id)).thenReturn(customer);

        CustomerLookupResult result = tools.getCustomer(id);

        assertThat(result.found()).isTrue();
        assertThat(result.customer().name()).isEqualTo("Beta Industries");
        assertThat(result.message()).isNull();
    }

    @Test
    void getCustomerReturnsANotFoundResultRatherThanPropagatingTheException() {
        UUID id = UUID.randomUUID();
        when(customerService.getById(id)).thenThrow(new CustomerNotFoundException(id));

        CustomerLookupResult result = tools.getCustomer(id);

        assertThat(result.found()).isFalse();
        assertThat(result.customer()).isNull();
        assertThat(result.message()).contains("No customer found");
    }

    @Test
    void getCustomerRejectsANullId() {
        assertThatThrownBy(() -> tools.getCustomer(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCustomerHistoryMapsActivitiesAndUsesAFixedPageSizeOfFive() {
        UUID id = UUID.randomUUID();
        Customer customer = customer("Gamma LLC");
        CustomerActivity activity = new CustomerActivity(customer, CustomerActivityType.NOTE, "Called customer",
                UUID.randomUUID());
        when(customerService.getHistory(eq(id), eq(PageRequest.of(0, 5))))
                .thenReturn(new PageImpl<>(List.of(activity)));

        CustomerHistoryResult result = tools.getCustomerHistory(id);

        assertThat(result.found()).isTrue();
        assertThat(result.activities()).extracting(CustomerActivityToolResult::type).containsExactly("NOTE");
        assertThat(result.activities()).extracting(CustomerActivityToolResult::content)
                .containsExactly("Called customer");
    }

    @Test
    void getCustomerHistoryReturnsANotFoundResultRatherThanPropagatingTheException() {
        UUID id = UUID.randomUUID();
        when(customerService.getHistory(eq(id), any(Pageable.class))).thenThrow(new CustomerNotFoundException(id));

        CustomerHistoryResult result = tools.getCustomerHistory(id);

        assertThat(result.found()).isFalse();
        assertThat(result.activities()).isEmpty();
    }

    private static Customer customer(String name) {
        Organization organization = new Organization("Test Org");
        return new Customer(organization, name, "Company", "test@example.com", "1234567890", "Address", "GSTIN123",
                "Some notes");
    }
}
