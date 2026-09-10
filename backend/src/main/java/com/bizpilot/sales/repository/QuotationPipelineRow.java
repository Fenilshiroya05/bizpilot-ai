package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.QuotationStatus;

import java.math.BigDecimal;

/** Spring Data JPA interface projection for {@link QuotationRepository#countAndSumGroupedByStatus}. */
public interface QuotationPipelineRow {

    QuotationStatus getStatus();

    long getCount();

    BigDecimal getAmount();
}
