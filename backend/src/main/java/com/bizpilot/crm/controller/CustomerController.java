package com.bizpilot.crm.controller;

import com.bizpilot.crm.dto.CustomerActivityResponse;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerNoteRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.crm.dto.CustomerUpdateRequest;
import com.bizpilot.crm.entity.CustomerStatus;
import com.bizpilot.crm.mapper.CustomerActivityMapper;
import com.bizpilot.crm.mapper.CustomerMapper;
import com.bizpilot.crm.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

/**
 * Customer management (CLAUDE.md §10). Every endpoint requires the matching
 * {@code CUSTOMER_*} permission from Phase 6 (project instructions §13); the
 * organization is always resolved server-side from the authenticated tenant
 * context inside {@code CustomerService} — no endpoint here accepts an
 * organization id from the client in any form.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final CustomerActivityMapper activityMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper,
                               CustomerActivityMapper activityMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
        this.activityMapper = activityMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        CustomerResponse response = customerMapper.toResponse(customerService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerMapper.toResponse(customerService.getById(id));
    }

    /**
     * Listing/search/filter/pagination in one endpoint (project instructions
     * §11). {@code q} performs a case-insensitive partial match against
     * name/company/email. {@code status} defaults to "every status except
     * ARCHIVED" when omitted; pass it explicitly (e.g. {@code ?status=ARCHIVED})
     * to view archived customers on purpose. Sorting/pagination use Spring
     * Data's standard {@code page}/{@code size}/{@code sort} parameters,
     * applied at the database level.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<CustomerResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.search(status, q, pageable).map(customerMapper::toResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerUpdateRequest request) {
        return customerMapper.toResponse(customerService.update(id, request));
    }

    /** Archive (soft-delete) — CLAUDE.md's "Delete/archive customer". */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        customerService.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public ResponseEntity<CustomerActivityResponse> addNote(@PathVariable UUID id,
                                                             @Valid @RequestBody CustomerNoteRequest request) {
        CustomerActivityResponse response = activityMapper.toResponse(
                customerService.addNote(id, request.content()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<CustomerActivityResponse> notes(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.getNotes(id, pageable).map(activityMapper::toResponse);
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<CustomerActivityResponse> activities(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.getActivities(id, pageable).map(activityMapper::toResponse);
    }

    /** Full chronological timeline — activities and notes merged. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<CustomerActivityResponse> history(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.getHistory(id, pageable).map(activityMapper::toResponse);
    }
}
