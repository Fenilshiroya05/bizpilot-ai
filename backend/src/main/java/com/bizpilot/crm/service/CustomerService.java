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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Owns all customer business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input, project
 * instructions §12) and every lookup by id is organization-scoped via
 * {@code findByIdAndOrganizationId} — there is no code path here that fetches
 * a customer by id alone.
 */
@Service
public class CustomerService {

    private static final List<CustomerActivityType> SYSTEM_ACTIVITY_TYPES =
            List.of(CustomerActivityType.CREATED, CustomerActivityType.STATUS_CHANGED, CustomerActivityType.ARCHIVED);
    private static final List<CustomerActivityType> NOTE_TYPES = List.of(CustomerActivityType.NOTE);

    private final CustomerRepository customerRepository;
    private final CustomerActivityRepository activityRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;
    private final CurrentUserProvider currentUserProvider;

    public CustomerService(CustomerRepository customerRepository, CustomerActivityRepository activityRepository,
                            TenantContext tenantContext, OrganizationService organizationService,
                            CurrentUserProvider currentUserProvider) {
        this.customerRepository = customerRepository;
        this.activityRepository = activityRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public Customer create(CustomerCreateRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Organization organization = organizationService.getCurrentOrganization();

        if (normalizedEmail != null
                && customerRepository.existsByOrganizationIdAndEmailIgnoreCase(organization.getId(), normalizedEmail)) {
            throw new DuplicateCustomerException(normalizedEmail);
        }

        Customer customer = new Customer(
                organization,
                request.name().trim(),
                trimToNull(request.company()),
                normalizedEmail,
                trimToNull(request.phone()),
                trimToNull(request.address()),
                trimToNull(request.gstin()),
                trimToNull(request.notes())
        );

        Customer saved;
        try {
            // saveAndFlush: surfaces the partial-unique-index violation here as a
            // clean 409, matching the pre-check-then-flush pattern already used by
            // identity.service.UserService for the equivalent race condition.
            saved = customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCustomerException(normalizedEmail);
        }

        recordActivity(saved, CustomerActivityType.CREATED, "Customer created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Customer getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(CustomerStatus statusFilter, String searchTerm, Pageable pageable) {
        UUID organizationId = tenantContext.currentOrganizationId();
        String normalizedSearch = normalizeSearch(searchTerm);
        if (statusFilter != null) {
            return customerRepository.searchByStatus(organizationId, statusFilter, normalizedSearch, pageable);
        }
        return customerRepository.searchExcludingArchived(organizationId, normalizedSearch, pageable);
    }

    @Transactional
    public Customer update(UUID id, CustomerUpdateRequest request) {
        Customer customer = findOrThrow(id);
        rejectIfArchived(customer);

        if (request.status() == CustomerStatus.ARCHIVED) {
            throw new InvalidCustomerDataException(
                    "Status cannot be set to ARCHIVED via update — use the archive operation instead");
        }

        if (request.name() != null) {
            if (!StringUtils.hasText(request.name())) {
                throw new InvalidCustomerDataException("name cannot be blank");
            }
            customer.setName(request.name().trim());
        }
        if (request.company() != null) {
            customer.setCompany(trimToNull(request.company()));
        }
        if (request.email() != null) {
            String normalizedEmail = normalizeEmail(request.email());
            if (normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(customer.getEmail())
                    && customerRepository.existsByOrganizationIdAndEmailIgnoreCase(
                            customer.getOrganization().getId(), normalizedEmail)) {
                throw new DuplicateCustomerException(normalizedEmail);
            }
            customer.setEmail(normalizedEmail);
        }
        if (request.phone() != null) {
            customer.setPhone(trimToNull(request.phone()));
        }
        if (request.address() != null) {
            customer.setAddress(trimToNull(request.address()));
        }
        if (request.gstin() != null) {
            customer.setGstin(trimToNull(request.gstin()));
        }
        if (request.notes() != null) {
            customer.setNotes(trimToNull(request.notes()));
        }
        if (request.status() != null && request.status() != customer.getStatus()) {
            CustomerStatus previous = customer.getStatus();
            customer.setStatus(request.status());
            recordActivity(customer, CustomerActivityType.STATUS_CHANGED,
                    "Status changed from " + previous + " to " + request.status());
        }

        try {
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCustomerException(customer.getEmail());
        }
    }

    @Transactional
    public void archive(UUID id) {
        Customer customer = findOrThrow(id);
        if (customer.isArchived()) {
            return; // already archived — archiving is idempotent, not an error
        }
        customer.setStatus(CustomerStatus.ARCHIVED);
        customerRepository.save(customer);
        recordActivity(customer, CustomerActivityType.ARCHIVED, "Customer archived");
    }

    @Transactional
    public CustomerActivity addNote(UUID id, String content) {
        Customer customer = findOrThrow(id);
        rejectIfArchived(customer);
        return recordActivity(customer, CustomerActivityType.NOTE, content.trim());
    }

    @Transactional(readOnly = true)
    public Page<CustomerActivity> getNotes(UUID id, Pageable pageable) {
        Customer customer = findOrThrow(id);
        return activityRepository.findByCustomerIdAndTypeIn(customer.getId(), NOTE_TYPES, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CustomerActivity> getActivities(UUID id, Pageable pageable) {
        Customer customer = findOrThrow(id);
        return activityRepository.findByCustomerIdAndTypeIn(customer.getId(), SYSTEM_ACTIVITY_TYPES, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CustomerActivity> getHistory(UUID id, Pageable pageable) {
        Customer customer = findOrThrow(id);
        return activityRepository.findByCustomerId(customer.getId(), pageable);
    }

    private Customer findOrThrow(UUID id) {
        return customerRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private void rejectIfArchived(Customer customer) {
        if (customer.isArchived()) {
            throw new CustomerArchivedException(customer.getId());
        }
    }

    private CustomerActivity recordActivity(Customer customer, CustomerActivityType type, String content) {
        UUID currentUserId = currentUserProvider.getCurrentUser()
                .map(UserPrincipal::userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No authenticated user context is available to attribute this activity"));
        CustomerActivity activity = new CustomerActivity(customer, type, content, currentUserId);
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
