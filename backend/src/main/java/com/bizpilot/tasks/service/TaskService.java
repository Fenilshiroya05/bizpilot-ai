package com.bizpilot.tasks.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.exception.InvalidAssigneeException;
import com.bizpilot.sales.exception.InvalidCustomerReferenceException;
import com.bizpilot.sales.repository.LeadRepository;
import com.bizpilot.tasks.dto.TaskAssignRequest;
import com.bizpilot.tasks.dto.TaskCreateRequest;
import com.bizpilot.tasks.dto.TaskUpdateRequest;
import com.bizpilot.tasks.entity.Task;
import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;
import com.bizpilot.tasks.exception.InvalidLeadReferenceException;
import com.bizpilot.tasks.exception.InvalidTaskDataException;
import com.bizpilot.tasks.exception.TaskNotFoundException;
import com.bizpilot.tasks.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Owns all task business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input) and
 * every lookup by id is organization-scoped via {@code findByIdAndOrganizationId}
 * — there is no code path here that fetches a task, assignee, customer, or
 * lead reference by id alone.
 *
 * <p>Reaches directly into {@code identity.repository.UserRepository},
 * {@code crm.repository.CustomerRepository}, and
 * {@code sales.repository.LeadRepository} (cross-module repository access)
 * for the sole purpose of the tenant-safe existence/ownership check — the
 * same established precedent as {@code LeadService} reaching into
 * {@code identity.repository.UserRepository} for assignee validation
 * (Phase 8): a narrow, single-method reuse, not a business-logic
 * duplication.
 *
 * <p><b>No immutability</b> — {@link #update} never rejects a request based
 * on the task's current status (contrast {@code InvoiceService.update}):
 * a task remains fully editable, and can be freely moved between any
 * non-{@code CANCELLED} status in either direction, at every status
 * including {@code COMPLETED}/{@code CANCELLED} (project instructions §7/§8
 * — an explicit, approved Phase 12 decision).
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;

    public TaskService(TaskRepository taskRepository, CustomerRepository customerRepository,
                        LeadRepository leadRepository, UserRepository userRepository,
                        TenantContext tenantContext, OrganizationService organizationService) {
        this.taskRepository = taskRepository;
        this.customerRepository = customerRepository;
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
    }

    @Transactional
    public Task create(TaskCreateRequest request) {
        Organization organization = organizationService.getCurrentOrganization();
        String title = requireTitle(request.title());
        TaskPriority priority = request.priority() != null ? request.priority() : TaskPriority.MEDIUM;
        UUID customerId = request.customerId() != null
                ? resolveCustomerId(request.customerId(), organization.getId()) : null;
        UUID leadId = request.leadId() != null
                ? resolveLeadId(request.leadId(), organization.getId()) : null;

        Task task = new Task(organization, title, trimToNull(request.description()), priority,
                request.dueDate(), customerId, leadId);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Task getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Task> search(TaskSearchCriteria criteria, Pageable pageable) {
        return taskRepository.search(
                tenantContext.currentOrganizationId(), criteria.status(), criteria.priority(),
                criteria.assignedToUserId(), criteria.unassignedOnly(), criteria.customerId(),
                criteria.leadId(), criteria.dueDateOnOrBefore(), normalizeSearch(criteria.search()), pageable);
    }

    @Transactional
    public Task update(UUID id, TaskUpdateRequest request) {
        Task task = findOrThrow(id);
        UUID organizationId = task.getOrganization().getId();

        if (request.title() != null) {
            task.setTitle(requireTitle(request.title()));
        }
        if (request.description() != null) {
            task.setDescription(trimToNull(request.description()));
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.clearDueDate()) {
            task.setDueDate(null);
        } else if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.clearCustomerId()) {
            task.setCustomerId(null);
        } else if (request.customerId() != null) {
            task.setCustomerId(resolveCustomerId(request.customerId(), organizationId));
        }
        if (request.clearLeadId()) {
            task.setLeadId(null);
        } else if (request.leadId() != null) {
            task.setLeadId(resolveLeadId(request.leadId(), organizationId));
        }
        if (request.notes() != null) {
            task.setNotes(trimToNull(request.notes()));
        }
        if (request.status() != null) {
            if (request.status() == TaskStatus.CANCELLED) {
                throw new InvalidTaskDataException(
                        "Status cannot be set to CANCELLED via update — use the cancel operation instead");
            }
            task.setStatus(request.status());
        }

        return taskRepository.save(task);
    }

    /**
     * Dedicated assignment action (project instructions §10) — a
     * {@code null} body {@code assigneeUserId} unassigns; otherwise the
     * assignee is validated to belong to the caller's organization and be
     * {@link UserStatus#ACTIVE}. Mirrors {@code LeadService.assign} exactly.
     */
    @Transactional
    public Task assign(UUID id, TaskAssignRequest request) {
        Task task = findOrThrow(id);
        UUID newAssigneeId = request.assigneeUserId();

        if (newAssigneeId == null) {
            task.setAssignedToUserId(null);
        } else {
            User assignee = userRepository.findByIdAndOrganizationId(newAssigneeId, task.getOrganization().getId())
                    .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new InvalidAssigneeException(newAssigneeId));
            task.setAssignedToUserId(assignee.getId());
        }

        return taskRepository.save(task);
    }

    /**
     * CLAUDE.md §23 names no "delete" operation distinct from the existing
     * {@code CANCELLED} status value — this transitions the task to that
     * status rather than introducing a second lifecycle field, mirroring
     * {@code QuotationService.cancel}/{@code InvoiceService.cancel}.
     * Idempotent: cancelling an already-cancelled task is a harmless no-op.
     * Never physically deletes the record.
     */
    @Transactional
    public void cancel(UUID id) {
        Task task = findOrThrow(id);
        if (task.isCancelled()) {
            return;
        }
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);
    }

    private Task findOrThrow(UUID id) {
        return taskRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private UUID resolveCustomerId(UUID customerId, UUID organizationId) {
        Customer customer = customerRepository.findByIdAndOrganizationId(customerId, organizationId)
                .orElseThrow(() -> new InvalidCustomerReferenceException(customerId));
        if (customer.isArchived()) {
            throw new InvalidCustomerReferenceException(customerId, "customer is archived");
        }
        return customer.getId();
    }

    private UUID resolveLeadId(UUID leadId, UUID organizationId) {
        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new InvalidLeadReferenceException(leadId));
        if (lead.isArchived()) {
            throw new InvalidLeadReferenceException(leadId, "lead is archived");
        }
        return lead.getId();
    }

    private static String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new InvalidTaskDataException("title cannot be blank");
        }
        return title.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeSearch(String search) {
        String trimmed = trimToNull(search);
        return trimmed == null ? null : "%" + trimmed.toLowerCase() + "%";
    }
}
