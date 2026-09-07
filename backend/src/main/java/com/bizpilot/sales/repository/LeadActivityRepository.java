package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.LeadActivity;
import com.bizpilot.sales.entity.LeadActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    /**
     * {@code leadId} alone is safe here: every caller resolves the parent
     * {@code Lead} via the org-scoped {@code findByIdAndOrganizationId}
     * lookup first (see {@code LeadService}).
     */
    Page<LeadActivity> findByLeadIdAndTypeIn(UUID leadId, Collection<LeadActivityType> types, Pageable pageable);

    Page<LeadActivity> findByLeadId(UUID leadId, Pageable pageable);
}
