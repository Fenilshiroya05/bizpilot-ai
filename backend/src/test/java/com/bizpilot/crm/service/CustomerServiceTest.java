package com.bizpilot.crm.service;

import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerUpdateRequest;
import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.entity.CustomerActivity;
import com.bizpilot.crm.entity.CustomerActivityType;
import com.bizpilot.crm.entity.CustomerStatus;
import com.bizpilot.crm.exception.CustomerArchivedException;
import com.bizpilot.crm.exception.CustomerNotFoundException;
import com.bizpilot.crm.exception.DuplicateCustomerException;
import com.bizpilot.crm.exception.InvalidCustomerDataException;
import com.bizpilot.crm.repository.CustomerActivityRepository;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerActivityRepository activityRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID otherOrganizationId = UUID.randomUUID();
    private final UUID currentUserId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme Corp");

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(currentUserProvider.getCurrentUser())
                .thenReturn(Optional.of(new UserPrincipal(currentUserId, organizationId, Set.of("CUSTOMER_READ"))));
        lenient().when(activityRepository.save(any(CustomerActivity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CustomerService service() {
        return new CustomerService(customerRepository, activityRepository, tenantContext, organizationService,
                currentUserProvider);
    }

    private CustomerCreateRequest createRequest(String email) {
        return new CustomerCreateRequest("Jane Doe", "Acme", email, "+1 555 0100", "1 Main St", null, "VIP");
    }

    @Test
    void createAssignsActiveStatusAndTheCurrentOrganizationAndRecordsACreatedActivity() {
        when(customerRepository.existsByOrganizationIdAndEmailIgnoreCase(any(), any())).thenReturn(false);
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = service().create(createRequest("jane@example.com"));

        assertThat(created.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(created.getOrganization()).isSameAs(organization);
        assertThat(created.getEmail()).isEqualTo("jane@example.com");

        ArgumentCaptor<CustomerActivity> activityCaptor = ArgumentCaptor.forClass(CustomerActivity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getType()).isEqualTo(CustomerActivityType.CREATED);
        assertThat(activityCaptor.getValue().getCreatedByUserId()).isEqualTo(currentUserId);
    }

    @Test
    void createRejectsAnEmailAlreadyUsedByAnotherCustomerInTheSameOrganization() {
        // any() for the organization id: the fixture `organization` here is an
        // unsaved entity (no generated id yet), unlike production where
        // OrganizationService.getCurrentOrganization() always returns an
        // already-persisted organization with a real id. This test only cares
        // about the email-match branch.
        when(customerRepository.existsByOrganizationIdAndEmailIgnoreCase(any(), eq("taken@example.com")))
                .thenReturn(true);

        assertThatThrownBy(() -> service().create(createRequest("taken@example.com")))
                .isInstanceOf(DuplicateCustomerException.class);

        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAllowsNoEmailAtAll() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = service().create(createRequest(null));

        assertThat(created.getEmail()).isNull();
        verify(customerRepository, never()).existsByOrganizationIdAndEmailIgnoreCase(any(), any());
    }

    @Test
    void getByIdThrowsNotFoundWhenNoCustomerMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyTheFieldsSuppliedAndLeavesOthersUnchanged() {
        Customer existing = new Customer(organization, "Original Name", "Original Co", "orig@example.com",
                "111", "Addr", null, "notes");
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerUpdateRequest request = new CustomerUpdateRequest("New Name", null, null, null, null, null, null, null);
        Customer updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getCompany()).isEqualTo("Original Co");
        assertThat(updated.getEmail()).isEqualTo("orig@example.com");
    }

    @Test
    void updateRejectsBlankName() {
        Customer existing = new Customer(organization, "Original Name", null, null, null, null, null, null);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        CustomerUpdateRequest request = new CustomerUpdateRequest("   ", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerDataException.class);
    }

    @Test
    void updateRejectsSettingStatusToArchivedDirectly() {
        Customer existing = new Customer(organization, "Name", null, null, null, null, null, null);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        CustomerUpdateRequest request = new CustomerUpdateRequest(
                null, null, null, null, null, null, null, CustomerStatus.ARCHIVED);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerDataException.class);
        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsAnyModificationToAnArchivedCustomer() {
        Customer archived = new Customer(organization, "Name", null, null, null, null, null, null);
        archived.setStatus(CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        CustomerUpdateRequest request = new CustomerUpdateRequest(
                "New Name", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(CustomerArchivedException.class);
    }

    @Test
    void updateRecordsAStatusChangedActivityWhenStatusActuallyChanges() {
        Customer existing = new Customer(organization, "Name", null, null, null, null, null, null);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerUpdateRequest request = new CustomerUpdateRequest(
                null, null, null, null, null, null, null, CustomerStatus.INACTIVE);
        service().update(UUID.randomUUID(), request);

        ArgumentCaptor<CustomerActivity> activityCaptor = ArgumentCaptor.forClass(CustomerActivity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getType()).isEqualTo(CustomerActivityType.STATUS_CHANGED);
    }

    @Test
    void archiveSetsStatusAndRecordsAnArchivedActivity() {
        Customer existing = new Customer(organization, "Name", null, null, null, null, null, null);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().archive(UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo(CustomerStatus.ARCHIVED);
        ArgumentCaptor<CustomerActivity> activityCaptor = ArgumentCaptor.forClass(CustomerActivity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getType()).isEqualTo(CustomerActivityType.ARCHIVED);
    }

    @Test
    void archivingAnAlreadyArchivedCustomerIsIdempotentAndDoesNotRecordASecondActivity() {
        Customer archived = new Customer(organization, "Name", null, null, null, null, null, null);
        archived.setStatus(CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        service().archive(UUID.randomUUID());

        verify(activityRepository, never()).save(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void addNoteRejectsNotesOnAnArchivedCustomer() {
        Customer archived = new Customer(organization, "Name", null, null, null, null, null, null);
        archived.setStatus(CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service().addNote(UUID.randomUUID(), "hello"))
                .isInstanceOf(CustomerArchivedException.class);
    }

    @Test
    void searchWithNoStatusFilterExcludesArchivedCustomers() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Customer> emptyPage = new PageImpl<>(java.util.List.of());
        when(customerRepository.searchExcludingArchived(eq(organizationId), any(), eq(pageable)))
                .thenReturn(emptyPage);

        service().search(null, null, pageable);

        verify(customerRepository).searchExcludingArchived(eq(organizationId), any(), eq(pageable));
        verify(customerRepository, never()).searchByStatus(any(), any(), any(), any());
    }

    @Test
    void searchWithAnExplicitStatusFilterUsesTheExactStatusMatchIncludingArchived() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Customer> emptyPage = new PageImpl<>(java.util.List.of());
        when(customerRepository.searchByStatus(eq(organizationId), eq(CustomerStatus.ARCHIVED), any(), eq(pageable)))
                .thenReturn(emptyPage);

        service().search(CustomerStatus.ARCHIVED, null, pageable);

        verify(customerRepository).searchByStatus(eq(organizationId), eq(CustomerStatus.ARCHIVED), any(), eq(pageable));
        verify(customerRepository, never()).searchExcludingArchived(any(), any(), any());
    }
}
