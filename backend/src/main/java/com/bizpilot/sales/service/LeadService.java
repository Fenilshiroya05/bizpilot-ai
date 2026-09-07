package com.bizpilot.sales.service;

import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.sales.dto.LeadAssignRequest;
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadUpdateRequest;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadActivity;
import com.bizpilot.sales.entity.LeadActivityType;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.exception.InvalidAssigneeException;
import com.bizpilot.sales.exception.InvalidLeadDataException;
import com.bizpilot.sales.exception.LeadArchivedException;
import com.bizpilot.sales.exception.LeadNotFoundException;
import com.bizpilot.sales.repository.LeadActivityRepository;
import com.bizpilot.sales.repository.LeadRepository;
import com.bizpilot.security.CurrentUserProvider;
import com.bizpilot.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns all lead business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input, project
 * instructions §16) and every lookup by id is organization-scoped via
 * {@code findByIdAndOrganizationId} — there is no code path here that fetches
 * a lead (or validates an assignee) by id alone.
 */
@Service
public class LeadService {

    private static final List<LeadActivityType> SYSTEM_ACTIVITY_TYPES = List.of(
            LeadActivityType.CREATED, LeadActivityType.STATUS_CHANGED,
            LeadActivityType.ASSIGNED, LeadActivityType.ARCHIVED);
    private static final List<LeadActivityType> NOTE_TYPES = List.of(LeadActivityType.NOTE);

    private final LeadRepository leadRepository;
    private final LeadActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;
    private final CurrentUserProvider currentUserProvider;

    public LeadService(LeadRepository leadRepository, LeadActivityRepository activityRepository,
                        UserRepository userRepository, TenantContext tenantContext,
                        OrganizationService organizationService, CurrentUserProvider currentUserProvider) {
        this.leadRepository = leadRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public Lead create(LeadCreateRequest request) {
        Organization organization = organizationService.getCurrentOrganization();
        LeadPriority priority = request.priority() != null ? request.priority() : LeadPriority.MEDIUM;

        Lead lead = new Lead(
                organization,
                request.name().trim(),
                trimToNull(request.company()),
                normalizeEmail(request.email()),
                trimToNull(request.phone()),
                request.source(),
                priority,
                request.followUpDate()
        );

        Lead saved = leadRepository.save(lead);
        recordActivity(saved, LeadActivityType.CREATED, "Lead created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Lead getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Lead> search(LeadSearchCriteria criteria, Pageable pageable) {
        UUID organizationId = tenantContext.currentOrganizationId();
        return leadRepository.search(
                organizationId,
                criteria.archivedOnly(),
                criteria.status(),
                criteria.source(),
                criteria.priority(),
                criteria.assignedToUserId(),
                criteria.unassignedOnly(),
                criteria.followUpOnOrBefore(),
                normalizeSearch(criteria.search()),
                pageable
        );
    }

    @Transactional
    public Lead update(UUID id, LeadUpdateRequest request) {
        Lead lead = findOrThrow(id);
        rejectIfArchived(lead);

        if (request.name() != null) {
            if (!StringUtils.hasText(request.name())) {
                throw new InvalidLeadDataException("name cannot be blank");
            }
            lead.setName(request.name().trim());
        }
        if (request.company() != null) {
            lead.setCompany(trimToNull(request.company()));
        }
        if (request.email() != null) {
            lead.setEmail(normalizeEmail(request.email()));
        }
        if (request.phone() != null) {
            lead.setPhone(trimToNull(request.phone()));
        }
        if (request.source() != null) {
            lead.setSource(request.source());
        }
        if (request.priority() != null) {
            lead.setPriority(request.priority());
        }
        if (request.clearFollowUpDate()) {
            lead.setFollowUpDate(null);
        } else if (request.followUpDate() != null) {
            lead.setFollowUpDate(request.followUpDate());
        }
        if (request.status() != null && request.status() != lead.getStatus()) {
            var previous = lead.getStatus();
            lead.setStatus(request.status());
            recordActivity(lead, LeadActivityType.STATUS_CHANGED,
                    "Status changed from " + previous + " to " + request.status());
        }

        return leadRepository.save(lead);
    }

    @Transactional
    public Lead assign(UUID id, LeadAssignRequest request) {
        Lead lead = findOrThrow(id);
        rejectIfArchived(lead);

        UUID newAssigneeId = request.assigneeUserId();
        UUID previousAssigneeId = lead.getAssignedToUserId();
        if (java.util.Objects.equals(newAssigneeId, previousAssigneeId)) {
            return lead; // no-op: already in the requested assignment state
        }

        String content;
        if (newAssigneeId == null) {
            lead.setAssignedToUserId(null);
            content = "Unassigned (previously assigned to " + previousAssigneeId + ")";
        } else {
            User assignee = userRepository.findByIdAndOrganizationId(newAssigneeId, lead.getOrganization().getId())
                    .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new InvalidAssigneeException(newAssigneeId));
            lead.setAssignedToUserId(assignee.getId());
            content = previousAssigneeId == null
                    ? "Assigned to " + assignee.getId()
                    : "Reassigned from " + previousAssigneeId + " to " + assignee.getId();
        }

        Lead saved = leadRepository.save(lead);
        recordActivity(saved, LeadActivityType.ASSIGNED, content);
        return saved;
    }

    @Transactional
    public void archive(UUID id) {
        Lead lead = findOrThrow(id);
        if (lead.isArchived()) {
            return; // already archived — archiving is idempotent, not an error
        }
        lead.setArchivedAt(Instant.now());
        leadRepository.save(lead);
        recordActivity(lead, LeadActivityType.ARCHIVED, "Lead archived");
    }

    @Transactional
    public LeadActivity addNote(UUID id, String content) {
        Lead lead = findOrThrow(id);
        rejectIfArchived(lead);
        return recordActivity(lead, LeadActivityType.NOTE, content.trim());
    }

    @Transactional(readOnly = true)
    public Page<LeadActivity> getNotes(UUID id, Pageable pageable) {
        Lead lead = findOrThrow(id);
        return activityRepository.findByLeadIdAndTypeIn(lead.getId(), NOTE_TYPES, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeadActivity> getActivities(UUID id, Pageable pageable) {
        Lead lead = findOrThrow(id);
        return activityRepository.findByLeadIdAndTypeIn(lead.getId(), SYSTEM_ACTIVITY_TYPES, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeadActivity> getHistory(UUID id, Pageable pageable) {
        Lead lead = findOrThrow(id);
        return activityRepository.findByLeadId(lead.getId(), pageable);
    }

    private Lead findOrThrow(UUID id) {
        return leadRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new LeadNotFoundException(id));
    }

    private void rejectIfArchived(Lead lead) {
        if (lead.isArchived()) {
            throw new LeadArchivedException(lead.getId());
        }
    }

    private LeadActivity recordActivity(Lead lead, LeadActivityType type, String content) {
        UUID currentUserId = currentUserProvider.getCurrentUser()
                .map(UserPrincipal::userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No authenticated user context is available to attribute this activity"));
        LeadActivity activity = new LeadActivity(lead, type, content, currentUserId);
        return activityRepository.save(activity);
    }

    private static String normalizeEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private static String normalizeSearch(String search) {
        String trimmed = trimToNull(search);
        return trimmed == null ? null : "%" + trimmed.toLowerCase() + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
