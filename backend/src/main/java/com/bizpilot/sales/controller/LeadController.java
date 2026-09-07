package com.bizpilot.sales.controller;

import com.bizpilot.sales.dto.LeadActivityResponse;
import com.bizpilot.sales.dto.LeadAssignRequest;
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadNoteRequest;
import com.bizpilot.sales.dto.LeadResponse;
import com.bizpilot.sales.dto.LeadUpdateRequest;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.sales.mapper.LeadActivityMapper;
import com.bizpilot.sales.mapper.LeadMapper;
import com.bizpilot.sales.service.LeadSearchCriteria;
import com.bizpilot.sales.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lead management (CLAUDE.md §11). Every endpoint requires the matching
 * {@code LEAD_*} permission from Phase 6 (project instructions §17); the
 * organization is always resolved server-side from the authenticated tenant
 * context inside {@code LeadService} — no endpoint here accepts an
 * organization id from the client in any form.
 */
@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadMapper leadMapper;
    private final LeadActivityMapper activityMapper;

    public LeadController(LeadService leadService, LeadMapper leadMapper, LeadActivityMapper activityMapper) {
        this.leadService = leadService;
        this.leadMapper = leadMapper;
        this.activityMapper = activityMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAD_CREATE')")
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody LeadCreateRequest request) {
        LeadResponse response = leadMapper.toResponse(leadService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/leads/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public LeadResponse get(@PathVariable UUID id) {
        return leadMapper.toResponse(leadService.getById(id));
    }

    /**
     * Listing/search/filter/pagination in one endpoint (project instructions
     * §15). {@code q} performs a case-insensitive partial match against
     * name/company/email. {@code archived} defaults to excluding archived
     * leads; pass {@code ?archived=true} to view archived leads instead.
     * {@code unassigned=true} restricts to leads with no current assignee
     * (mutually exclusive in practice with {@code assignedToUserId} — both
     * may be supplied but will simply match nothing unless consistent).
     * Sorting/pagination use Spring Data's standard {@code page}/{@code size}/
     * {@code sort} parameters, applied at the database level.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public Page<LeadResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadSource source,
            @RequestParam(required = false) LeadPriority priority,
            @RequestParam(required = false) UUID assignedToUserId,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpBefore,
            @RequestParam(required = false, defaultValue = "false") boolean archived,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        LeadSearchCriteria criteria = new LeadSearchCriteria(
                status, source, priority, assignedToUserId, unassigned, followUpBefore, archived, q);
        return leadService.search(criteria, pageable).map(leadMapper::toResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    public LeadResponse update(@PathVariable UUID id, @Valid @RequestBody LeadUpdateRequest request) {
        return leadMapper.toResponse(leadService.update(id, request));
    }

    /** Archive (soft-delete) — CLAUDE.md's "Lead deletion/archive". */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_DELETE')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        leadService.archive(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dedicated assignment action (project instructions §7) — a
     * {@code null} body {@code assigneeUserId} unassigns; otherwise the
     * assignee is validated to belong to the caller's organization.
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    public LeadResponse assign(@PathVariable UUID id, @RequestBody LeadAssignRequest request) {
        return leadMapper.toResponse(leadService.assign(id, request));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    public ResponseEntity<LeadActivityResponse> addNote(@PathVariable UUID id,
                                                         @Valid @RequestBody LeadNoteRequest request) {
        LeadActivityResponse response = activityMapper.toResponse(leadService.addNote(id, request.content()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public Page<LeadActivityResponse> notes(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return leadService.getNotes(id, pageable).map(activityMapper::toResponse);
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public Page<LeadActivityResponse> activities(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return leadService.getActivities(id, pageable).map(activityMapper::toResponse);
    }

    /** Full chronological timeline — activities and notes merged. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    public Page<LeadActivityResponse> history(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return leadService.getHistory(id, pageable).map(activityMapper::toResponse);
    }
}
