package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.InvoiceToolResult;
import com.bizpilot.crm.entity.Customer;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.sales.entity.Invoice;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceToolsTest {

    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final InvoiceTools tools = new InvoiceTools(invoiceService);

    @Test
    void getOutstandingInvoicesDelegatesToInvoiceServiceWithAFixedPageSizeOfFive() {
        Invoice invoice = invoice();
        when(invoiceService.getOutstanding(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(invoice)));

        List<InvoiceToolResult> results = tools.getOutstandingInvoices();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(results.get(0).customerId()).isEqualTo(invoice.getCustomer().getId());
        verify(invoiceService).getOutstanding(eq(PageRequest.of(0, 5)));
    }

    @Test
    void getOutstandingInvoicesReturnsAnEmptyListWhenNothingIsOutstanding() {
        when(invoiceService.getOutstanding(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        List<InvoiceToolResult> results = tools.getOutstandingInvoices();

        assertThat(results).isEmpty();
    }

    private static Invoice invoice() {
        Organization organization = new Organization("Test Org");
        Customer customer = new Customer(organization, "Zeta Ltd", "Company", "zeta@example.com", "1234567890",
                "Address", "GSTIN123", "Some notes");
        Invoice invoice = new Invoice(organization, customer, LocalDate.now().plusDays(7));
        invoice.setStatus(InvoiceStatus.ISSUED);
        return invoice;
    }
}
