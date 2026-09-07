package com.bizpilot.sales.controller;

import com.bizpilot.sales.dto.InvoiceCreateRequest;
import com.bizpilot.sales.dto.InvoiceResponse;
import com.bizpilot.sales.dto.InvoiceSummaryResponse;
import com.bizpilot.sales.dto.InvoiceUpdateRequest;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.mapper.InvoiceMapper;
import com.bizpilot.sales.service.InvoicePdfService;
import com.bizpilot.sales.service.InvoiceSearchCriteria;
import com.bizpilot.sales.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Invoice management (CLAUDE.md §15). Every endpoint requires the matching
 * {@code INVOICE_*} permission — newly seeded by V9 (Phase 11). The
 * organization is always resolved server-side from the authenticated tenant
 * context inside {@code InvoiceService} — no endpoint here accepts an
 * organization id from the client in any form. Mirrors
 * {@code QuotationController} exactly in structure.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final InvoiceMapper invoiceMapper;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService,
                              InvoiceMapper invoiceMapper) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.invoiceMapper = invoiceMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceCreateRequest request) {
        InvoiceResponse response = invoiceMapper.toResponse(invoiceService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/invoices/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoiceMapper.toResponse(invoiceService.getById(id));
    }

    /**
     * Listing/search/filter/pagination. No free-text search field exists —
     * {@code Invoice} has no natural text field of its own. Response rows
     * omit {@code items} ({@link InvoiceSummaryResponse}) — full item detail
     * is available via {@link #get}.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public Page<InvoiceSummaryResponse> search(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate dueDateBefore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        InvoiceSearchCriteria criteria = new InvoiceSearchCriteria(status, customerId, dueDateBefore);
        return invoiceService.search(criteria, pageable).map(invoiceMapper::toSummaryResponse);
    }

    /** Only permitted while the invoice is still DRAFT — see {@code InvoiceService.update}'s Javadoc. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICE_UPDATE')")
    public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceUpdateRequest request) {
        return invoiceMapper.toResponse(invoiceService.update(id, request));
    }

    /** Cancel — CLAUDE.md's invoice "delete," modeled as a transition to the existing CANCELLED status. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICE_DELETE')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        invoiceService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    /** Generated on demand, never persisted. Read-only, so gated by INVOICE_READ. */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] pdf = invoicePdfService.generatePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .body(pdf);
    }
}
