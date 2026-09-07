package com.bizpilot.tasks.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.entity.CustomerStatus;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.identity.entity.Role;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private OrganizationService organizationService;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme Corp");
    private final Customer customer = new Customer(organization, "Jane Customer", null, null, null, null, null, null);
    private final Lead lead = new Lead(organization, "Jane Lead", null, null, null,
            LeadSource.WEBSITE, LeadPriority.MEDIUM, null);

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TaskService service() {
        return new TaskService(taskRepository, customerRepository, leadRepository, userRepository,
                tenantContext, organizationService);
    }

    private TaskCreateRequest createRequest() {
        return new TaskCreateRequest("Follow up with customer", null, null, null, null, null);
    }

    @Test
    void createAssignsDefaultStatusAndPriorityAndTheCurrentOrganization() {
        Task created = service().create(createRequest());

        assertThat(created.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(created.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(created.getOrganization()).isSameAs(organization);
        assertThat(created.getAssignedToUserId()).isNull();
        assertThat(created.getTitle()).isEqualTo("Follow up with customer");
    }

    @Test
    void createHonorsAnExplicitPriorityWhenSupplied() {
        TaskCreateRequest request = new TaskCreateRequest("Title", null, TaskPriority.URGENT, null, null, null);

        Task created = service().create(request);

        assertThat(created.getPriority()).isEqualTo(TaskPriority.URGENT);
    }

    @Test
    void createRejectsABlankTitle() {
        TaskCreateRequest request = new TaskCreateRequest("   ", null, null, null, null, null);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(InvalidTaskDataException.class);
    }

    @Test
    void createValidatesTheCustomerBelongsToTheCurrentOrganization() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.empty());

        TaskCreateRequest request = new TaskCreateRequest("Title", null, null, null, customerId, null);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(InvalidCustomerReferenceException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createRejectsAnArchivedCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer archivedCustomer = new Customer(organization, "Old Customer", null, null, null, null, null, null);
        archivedCustomer.setStatus(CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(archivedCustomer));

        TaskCreateRequest request = new TaskCreateRequest("Title", null, null, null, customerId, null);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(InvalidCustomerReferenceException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createValidatesTheLeadBelongsToTheCurrentOrganization() {
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findByIdAndOrganizationId(eq(leadId), any())).thenReturn(Optional.empty());

        TaskCreateRequest request = new TaskCreateRequest("Title", null, null, null, null, leadId);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(InvalidLeadReferenceException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createRejectsAnArchivedLead() {
        UUID leadId = UUID.randomUUID();
        Lead archivedLead = new Lead(organization, "Old Lead", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        archivedLead.setArchivedAt(java.time.Instant.now());
        when(leadRepository.findByIdAndOrganizationId(eq(leadId), any())).thenReturn(Optional.of(archivedLead));

        TaskCreateRequest request = new TaskCreateRequest("Title", null, null, null, null, leadId);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(InvalidLeadReferenceException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createSucceedsWithValidCustomerAndLeadReferences() {
        UUID customerId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(leadRepository.findByIdAndOrganizationId(eq(leadId), any())).thenReturn(Optional.of(lead));

        TaskCreateRequest request = new TaskCreateRequest("Title", null, null, null, customerId, leadId);
        Task created = service().create(request);

        assertThat(created.getCustomerId()).isEqualTo(customer.getId());
        assertThat(created.getLeadId()).isEqualTo(lead.getId());
    }

    @Test
    void getByIdThrowsNotFoundWhenNoTaskMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyTheFieldsSuppliedAndLeavesOthersUnchanged() {
        Task existing = new Task(organization, "Original", "Original description", TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                "New Title", null, null, null, false, null, false, null, false, null, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getDescription()).isEqualTo("Original description");
        assertThat(updated.getPriority()).isEqualTo(TaskPriority.LOW);
    }

    @Test
    void updateRejectsBlankTitle() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                "   ", null, null, null, false, null, false, null, false, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidTaskDataException.class);
    }

    @Test
    void updateRejectsSettingStatusToCancelledDirectly() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, TaskStatus.CANCELLED, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidTaskDataException.class);
        verify(taskRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"TODO", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void aTaskRemainsFullyEditableAtEveryCurrentStatus(TaskStatus currentStatus) {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        existing.setStatus(currentStatus);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                "Updated Title", null, null, null, false, null, false, null, false, null, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void reopeningACompletedTaskToTodoSucceeds() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        existing.setStatus(TaskStatus.COMPLETED);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, TaskStatus.TODO, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void reopeningACancelledTaskToInProgressSucceeds() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        existing.setStatus(TaskStatus.CANCELLED);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, TaskStatus.IN_PROGRESS, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateClearsDueDateWhenExplicitlyRequested() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW,
                LocalDate.now().plusDays(5), null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, true, null, false, null, false, null, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getDueDate()).isNull();
    }

    @Test
    void updateClearsCustomerReferenceWhenExplicitlyRequested() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, customer.getId(), null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, true, null, false, null, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getCustomerId()).isNull();
    }

    @Test
    void updateClearsLeadReferenceWhenExplicitlyRequested() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, lead.getId());
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, true, null, null);
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getLeadId()).isNull();
    }

    @Test
    void updateValidatesANewCustomerReferenceBelongsToTheCurrentOrganization() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID newCustomerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(newCustomerId), any())).thenReturn(Optional.empty());

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, newCustomerId, false, null, false, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerReferenceException.class);
    }

    @Test
    void updateValidatesANewLeadReferenceIsNotArchived() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID newLeadId = UUID.randomUUID();
        Lead archivedLead = new Lead(organization, "Old Lead", null, null, null,
                LeadSource.OTHER, LeadPriority.LOW, null);
        archivedLead.setArchivedAt(java.time.Instant.now());
        when(leadRepository.findByIdAndOrganizationId(eq(newLeadId), any())).thenReturn(Optional.of(archivedLead));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, false, newLeadId, false, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidLeadReferenceException.class);
    }

    @Test
    void updateOverwritesNotesAndClearingIsAchievedWithAnEmptyString() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        existing.setNotes("old note");
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        TaskUpdateRequest request = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, null, "");
        Task updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getNotes()).isNull();
    }

    @Test
    void assignValidatesTheAssigneeBelongsToTheCurrentOrganization() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID assigneeId = UUID.randomUUID();
        when(userRepository.findByIdAndOrganizationId(eq(assigneeId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assign(UUID.randomUUID(), new TaskAssignRequest(assigneeId)))
                .isInstanceOf(InvalidAssigneeException.class);
    }

    @Test
    void assignRejectsAssigningToANonActiveUser() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID assigneeId = UUID.randomUUID();
        User disabledAssignee = new User("disabled@example.com", "hash", "A", "B", UserStatus.DISABLED, organization,
                new Role("EMPLOYEE"));
        when(userRepository.findByIdAndOrganizationId(eq(assigneeId), any())).thenReturn(Optional.of(disabledAssignee));

        assertThatThrownBy(() -> service().assign(UUID.randomUUID(), new TaskAssignRequest(assigneeId)))
                .isInstanceOf(InvalidAssigneeException.class);
    }

    @Test
    void assignSucceedsWhenAssigneeIsValid() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID assigneeId = UUID.randomUUID();
        User assignee = new User("assignee@example.com", "hash", "A", "B", UserStatus.ACTIVE, organization,
                new Role("EMPLOYEE"));
        when(userRepository.findByIdAndOrganizationId(eq(assigneeId), any())).thenReturn(Optional.of(assignee));

        Task assigned = service().assign(UUID.randomUUID(), new TaskAssignRequest(assigneeId));

        assertThat(assigned.getAssignedToUserId()).isEqualTo(assignee.getId());
    }

    @Test
    void assignToNullUnassignsTheTask() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        existing.setAssignedToUserId(UUID.randomUUID());
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        Task unassigned = service().assign(UUID.randomUUID(), new TaskAssignRequest(null));

        assertThat(unassigned.getAssignedToUserId()).isNull();
    }

    @Test
    void cancelSetsStatusToCancelled() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotentWhenAlreadyCancelled() {
        Task existing = new Task(organization, "Original", null, TaskPriority.LOW, null, null, null);
        existing.setStatus(TaskStatus.CANCELLED);
        when(taskRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        verify(taskRepository, never()).save(any());
    }

    @Test
    void searchDelegatesToTheRepositoryWithTheCurrentOrganizationId() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Task> emptyPage = new PageImpl<>(List.of());
        when(taskRepository.search(eq(organizationId), any(), any(), any(), eq(false), any(), any(), any(), any(),
                eq(pageable))).thenReturn(emptyPage);

        TaskSearchCriteria criteria = new TaskSearchCriteria(null, null, null, false, null, null, null, null);
        service().search(criteria, pageable);

        verify(taskRepository).search(eq(organizationId), any(), any(), any(), eq(false), any(), any(), any(), any(),
                eq(pageable));
    }
}
