package com.bizpilot.tasks.controller;

import com.bizpilot.tasks.dto.TaskAssignRequest;
import com.bizpilot.tasks.dto.TaskCreateRequest;
import com.bizpilot.tasks.dto.TaskResponse;
import com.bizpilot.tasks.dto.TaskUpdateRequest;
import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;
import com.bizpilot.tasks.mapper.TaskMapper;
import com.bizpilot.tasks.service.TaskSearchCriteria;
import com.bizpilot.tasks.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Task management (CLAUDE.md §23). Every endpoint requires the matching
 * {@code TASK_*} permission, newly seeded by V10 (Phase 12). The
 * organization is always resolved server-side from the authenticated tenant
 * context inside {@code TaskService} — no endpoint here accepts an
 * organization id from the client in any form. Mirrors
 * {@code LeadController}/{@code QuotationController} in structure.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    public TaskController(TaskService taskService, TaskMapper taskMapper) {
        this.taskService = taskService;
        this.taskMapper = taskMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        TaskResponse response = taskMapper.toResponse(taskService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_READ')")
    public TaskResponse get(@PathVariable UUID id) {
        return taskMapper.toResponse(taskService.getById(id));
    }

    /**
     * Listing/search/filter/pagination in one endpoint, mirroring
     * {@code LeadController.search}. {@code q} performs a case-insensitive
     * partial match against {@code title}. {@code unassigned=true}
     * restricts to tasks with no current assignee. Sorting/pagination use
     * Spring Data's standard {@code page}/{@code size}/{@code sort}
     * parameters, applied at the database level.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TASK_READ')")
    public Page<TaskResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assignedToUserId,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateOnOrBefore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        TaskSearchCriteria criteria = new TaskSearchCriteria(
                status, priority, assignedToUserId, unassigned, customerId, leadId, dueDateOnOrBefore, q);
        return taskService.search(criteria, pageable).map(taskMapper::toResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskUpdateRequest request) {
        return taskMapper.toResponse(taskService.update(id, request));
    }

    /**
     * Dedicated assignment action — a {@code null} body {@code assigneeUserId}
     * unassigns; otherwise the assignee is validated to belong to the
     * caller's organization and be active.
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public TaskResponse assign(@PathVariable UUID id, @RequestBody TaskAssignRequest request) {
        return taskMapper.toResponse(taskService.assign(id, request));
    }

    /** Cancel — CLAUDE.md's task "delete," modeled as a transition to the existing CANCELLED status. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_DELETE')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        taskService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
