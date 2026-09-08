package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.InvoiceToolResult;
import com.bizpilot.sales.entity.Invoice;
import com.bizpilot.sales.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Read-only AI tools over the organization's invoices (Phase 17, CLAUDE.md
 * §19). "Outstanding" is defined once, in {@link InvoiceService#getOutstanding},
 * not here — this class only calls it and maps the result (project
 * instructions §12/§17); no new aggregation/analytics capability is
 * introduced.
 */
@Component
public class InvoiceTools {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTools.class);

    private static final int MAX_RESULTS = 5;

    private final InvoiceService invoiceService;

    public InvoiceTools(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Tool(description = "Return the current organization's outstanding (issued, partially paid, or overdue) "
            + "invoices. Returns at most 5, ordered by creation date.")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public List<InvoiceToolResult> getOutstandingInvoices() {
        Instant start = Instant.now();
        Page<Invoice> page = invoiceService.getOutstanding(PageRequest.of(0, MAX_RESULTS));
        List<InvoiceToolResult> results = page.getContent().stream().map(InvoiceTools::toResult).toList();
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("AI tool call succeeded [tool=getOutstandingInvoices, resultCount={}, durationMs={}]",
                results.size(), durationMs);
        return results;
    }

    private static InvoiceToolResult toResult(Invoice invoice) {
        return new InvoiceToolResult(
                invoice.getId(),
                invoice.getCustomer().getId(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotal()
        );
    }
}
