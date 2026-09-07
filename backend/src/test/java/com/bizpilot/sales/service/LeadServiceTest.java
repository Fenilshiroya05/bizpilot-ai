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
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.sales.exception.InvalidAssigneeException;
import com.bizpilot.sales.exception.InvalidLeadDataException;
import com.bizpilot.sales.exception.LeadArchivedException;
import com.bizpilot.sales.exception.LeadNotFoundException;
import com.bizpilot.sales.repository.LeadActivityRepository;
import com.bizpilot.sales.repository.LeadRepository;
import com.bizpilot.security.CurrentUserProvider;
import com.bizpilot.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadActivityRepository activityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID currentUserId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme Corp");

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(currentUserProvider.getCurrentUser())
                .thenReturn(Optional.of(new UserPrincipal(currentUserId, organizationId, Set.of("LEAD_READ"))));
        lenient().when(activityRepository.save(any(LeadActivity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private LeadService service() {
        return new LeadService(leadRepository, activityRepository, userRepository, tenantContext,
                organizationService, currentUserProvider);
    }

    private LeadCreateRequest createRequest() {
        return new LeadCreateRequest("Jane Doe", "Acme", "jane@example.com", "+1 555 0100",
                LeadSource.WEBSITE, null, null);
    }

    @Test
    void createAssignsNewStatusDefaultPriorityAndTheCurrentOrganizationAndRecordsACreatedActivity() {
        Lead created = service().create(createRequest());

        assertThat(created.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(created.getPriority()).isEqualTo(LeadPriority.MEDIUM);
        assertThat(created.getOrganization()).isSameAs(organization);
        assertThat(created.getSource()).isEqualTo(LeadSource.WEBSITE);
        assertThat(created.getAssignedToUserId()).isNull();

        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LeadActivityType.CREATED);
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(currentUserId);
    }

    @Test
    void createHonorsAnExplicitPriorityWhenSupplied() {
        LeadCreateRequest request = new LeadCreateRequest(
                "Jane Doe", null, null, null, LeadSource.REFERRAL, LeadPriority.HIGH, null);

        Lead created = service().create(request);

        assertThat(created.getPriority()).isEqualTo(LeadPriority.HIGH);
    }

    @Test
    void getByIdThrowsNotFoundWhenNoLeadMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(LeadNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyTheFieldsSuppliedAndLeavesOthersUnchanged() {
        Lead existing = new Lead(organization, "Original Name", "Original Co", "orig@example.com",
                "111", LeadSource.EMAIL, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        LeadUpdateRequest request = new LeadUpdateRequest(
                "New Name", null, null, null, null, null, null, null, false);
        Lead updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getCompany()).isEqualTo("Original Co");
        assertThat(updated.getEmail()).isEqualTo("orig@example.com");
        assertThat(updated.getPriority()).isEqualTo(LeadPriority.LOW);
    }

    @Test
    void updateRejectsBlankName() {
        Lead existing = new Lead(organization, "Original Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        LeadUpdateRequest request = new LeadUpdateRequest(
                "   ", null, null, null, null, null, null, null, false);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidLeadDataException.class);
    }

    @Test
    void updateRejectsAnyModificationToAnArchivedLead() {
        Lead archived = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        archived.setArchivedAt(java.time.Instant.now());
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        LeadUpdateRequest request = new LeadUpdateRequest(
                "New Name", null, null, null, null, null, null, null, false);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(LeadArchivedException.class);
    }

    @Test
    void updateRecordsAStatusChangedActivityWhenStatusActuallyChanges() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        LeadUpdateRequest request = new LeadUpdateRequest(
                null, null, null, null, LeadStatus.CONTACTED, null, null, null, false);
        service().update(UUID.randomUUID(), request);

        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LeadActivityType.STATUS_CHANGED);
    }

    @Test
    void updateClearsFollowUpDateWhenExplicitlyRequested() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, LocalDate.now());
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        LeadUpdateRequest request = new LeadUpdateRequest(
                null, null, null, null, null, null, null, null, true);
        Lead updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getFollowUpDate()).isNull();
    }

    @Test
    void updateSetsFollowUpDateWhenSupplied() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        LocalDate newDate = LocalDate.now().plusDays(3);

        LeadUpdateRequest request = new LeadUpdateRequest(
                null, null, null, null, null, null, null, newDate, false);
        Lead updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getFollowUpDate()).isEqualTo(newDate);
    }

    @Test
    void assignValidatesTheAssigneeBelongsToTheCurrentOrganization() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID assigneeId = UUID.randomUUID();
        // any() for the organization id: `organization` here is an unsaved
        // fixture entity (no generated id yet), unlike production where
        // Lead.getOrganization() always returns an already-persisted
        // organization with a real id.
        when(userRepository.findByIdAndOrganizationId(eq(assigneeId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assign(UUID.randomUUID(), new LeadAssignRequest(assigneeId)))
                .isInstanceOf(InvalidAssigneeException.class);
    }

    @Test
    void assignRejectsAssigningToANonActiveUser() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID assigneeId = UUID.randomUUID();
        User disabledAssignee = new User("disabled@example.com", "hash", "A", "B", UserStatus.DISABLED, organization,
                new com.bizpilot.identity.entity.Role("EMPLOYEE"));
        when(userRepository.findByIdAndOrganizationId(eq(assigneeId), any())).thenReturn(Optional.of(disabledAssignee));

        assertThatThrownBy(() -> service().assign(UUID.randomUUID(), new LeadAssignRequest(assigneeId)))
                .isInstanceOf(InvalidAssigneeException.class);
    }

    @Test
    void assignSucceedsAndRecordsAnAssignedActivityWhenAssigneeIsValid() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID assigneeId = UUID.randomUUID();
        User assignee = new User("assignee@example.com", "hash", "A", "B", UserStatus.ACTIVE, organization,
                new com.bizpilot.identity.entity.Role("EMPLOYEE"));
        when(userRepository.findByIdAndOrganizationId(eq(assigneeId), any())).thenReturn(Optional.of(assignee));

        Lead assigned = service().assign(UUID.randomUUID(), new LeadAssignRequest(assigneeId));

        assertThat(assigned.getAssignedToUserId()).isEqualTo(assignee.getId());
        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LeadActivityType.ASSIGNED);
    }

    @Test
    void assignToNullUnassignsTheLead() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        existing.setAssignedToUserId(UUID.randomUUID());
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        Lead unassigned = service().assign(UUID.randomUUID(), new LeadAssignRequest(null));

        assertThat(unassigned.getAssignedToUserId()).isNull();
        verify(activityRepository).save(any(LeadActivity.class));
    }

    @Test
    void assigningToTheSameCurrentAssigneeIsANoOpAndDoesNotRecordAnActivity() {
        UUID assigneeId = UUID.randomUUID();
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        existing.setAssignedToUserId(assigneeId);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().assign(UUID.randomUUID(), new LeadAssignRequest(assigneeId));

        verify(activityRepository, never()).save(any());
        verify(userRepository, never()).findByIdAndOrganizationId(any(), any());
    }

    @Test
    void assignRejectsModifyingAnArchivedLead() {
        Lead archived = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        archived.setArchivedAt(java.time.Instant.now());
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service().assign(UUID.randomUUID(), new LeadAssignRequest(UUID.randomUUID())))
                .isInstanceOf(LeadArchivedException.class);
    }

    @Test
    void archiveSetsArchivedAtAndRecordsAnArchivedActivity() {
        Lead existing = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().archive(UUID.randomUUID());

        assertThat(existing.isArchived()).isTrue();
        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(LeadActivityType.ARCHIVED);
    }

    @Test
    void archivingAnAlreadyArchivedLeadIsIdempotentAndDoesNotRecordASecondActivity() {
        Lead archived = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        archived.setArchivedAt(java.time.Instant.now());
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        service().archive(UUID.randomUUID());

        verify(activityRepository, never()).save(any());
        verify(leadRepository, never()).save(any());
    }

    @Test
    void addNoteRejectsNotesOnAnArchivedLead() {
        Lead archived = new Lead(organization, "Name", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        archived.setArchivedAt(java.time.Instant.now());
        when(leadRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service().addNote(UUID.randomUUID(), "hello"))
                .isInstanceOf(LeadArchivedException.class);
    }

    @Test
    void searchDelegatesToTheRepositoryWithTheCurrentOrganizationId() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Lead> emptyPage = new PageImpl<>(List.of());
        when(leadRepository.search(eq(organizationId), eq(false), any(), any(), any(), any(), eq(false), any(),
                any(), eq(pageable))).thenReturn(emptyPage);

        LeadSearchCriteria criteria = new LeadSearchCriteria(null, null, null, null, false, null, false, null);
        service().search(criteria, pageable);

        verify(leadRepository).search(eq(organizationId), eq(false), any(), any(), any(), any(), eq(false), any(),
                any(), eq(pageable));
    }
}
