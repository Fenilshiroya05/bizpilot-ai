package com.bizpilot.sales.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Spring Data JPA interface projection for {@link InvoiceRepository#findPaidRevenuePointsCreatedOnOrAfter}. */
public interface InvoiceRevenuePoint {

    Instant getCreatedAt();

    BigDecimal getTotal();
}
