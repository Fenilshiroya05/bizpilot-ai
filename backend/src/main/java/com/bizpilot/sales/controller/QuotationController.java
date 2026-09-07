package com.bizpilot.sales.controller;

import com.bizpilot.sales.dto.QuotationCreateRequest;
import com.bizpilot.sales.dto.QuotationResponse;
import com.bizpilot.sales.dto.QuotationSummaryResponse;
import com.bizpilot.sales.dto.QuotationUpdateRequest;
import com.bizpilot.sales.entity.QuotationStatus;
import com.bizpilot.sales.mapper.QuotationMapper;
import com.bizpilot.sales.service.QuotationPdfService;
import com.bizpilot.sales.service.QuotationSearchCriteria;
import com.bizpilot.sales.service.QuotationService;
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
 * Quotation management (CLAUDE.md §14). Every endpoint requires the matching
 * {@code QUOTATION_*} permission — already seeded by Phase 6 (V4); no new
 * permission or migration was introduced (project instructions §16). The
 * organization is always resolved server-side from the authenticated tenant
 * context inside {@code QuotationService} — no endpoint here accepts an
 * organization id from the client in any form.
 */
@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService quotationService;
    private final QuotationPdfService quotationPdfService;
    private final QuotationMapper quotationMapper;

    public QuotationController(QuotationService quotationService, QuotationPdfService quotationPdfService,
                                QuotationMapper quotationMapper) {
        this.quotationService = quotationService;
        this.quotationPdfService = quotationPdfService;
        this.quotationMapper = quotationMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public ResponseEntity<QuotationResponse> create(@Valid @RequestBody QuotationCreateRequest request) {
        QuotationResponse response = quotationMapper.toResponse(quotationService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/quotations/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    public QuotationResponse get(@PathVariable UUID id) {
        return quotationMapper.toResponse(quotationService.getById(id));
    }

    /**
     * Listing/search/filter/pagination (project instructions §15). No
     * free-text search field exists — {@code Quotation} has no natural text
     * field of its own (see {@code QuotationRepository}'s Javadoc). Response
     * rows omit {@code items} ({@link QuotationSummaryResponse}) — full item
     * detail is available via {@link #get}.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    public Page<QuotationSummaryResponse> search(
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate validUntilBefore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        QuotationSearchCriteria criteria = new QuotationSearchCriteria(status, customerId, validUntilBefore);
        return quotationService.search(criteria, pageable).map(quotationMapper::toSummaryResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_UPDATE')")
    public QuotationResponse update(@PathVariable UUID id, @Valid @RequestBody QuotationUpdateRequest request) {
        return quotationMapper.toResponse(quotationService.update(id, request));
    }

    /** Cancel — CLAUDE.md's "Delete/cancel quotation," modeled as a transition to the existing CANCELLED status. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_DELETE')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        quotationService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    /** Generated on demand, never persisted (project instructions §19). Read-only, so gated by QUOTATION_READ. */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] pdf = quotationPdfService.generatePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quotation-" + id + ".pdf\"")
                .body(pdf);
    }
}
