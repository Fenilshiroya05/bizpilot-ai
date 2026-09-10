package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.LeadSource;

/** Spring Data JPA interface projection for {@link LeadRepository#countGroupedBySource}. */
public interface LeadSourceCount {

    LeadSource getSource();

    long getCount();
}
