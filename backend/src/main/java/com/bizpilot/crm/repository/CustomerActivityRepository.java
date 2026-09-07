package com.bizpilot.crm.repository;

import com.bizpilot.crm.entity.CustomerActivity;
import com.bizpilot.crm.entity.CustomerActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface CustomerActivityRepository extends JpaRepository<CustomerActivity, UUID> {

    /**
     * {@code customerId} alone is safe here: every caller resolves the parent
     * {@code Customer} via the org-scoped {@code findByIdAndOrganizationId}
     * lookup first (see {@code CustomerService}), so by the time this method
     * runs, {@code customerId} is already proven to belong to the caller's
     * organization.
     */
    Page<CustomerActivity> findByCustomerIdAndTypeIn(UUID customerId, Collection<CustomerActivityType> types,
                                                       Pageable pageable);

    Page<CustomerActivity> findByCustomerId(UUID customerId, Pageable pageable);
}
