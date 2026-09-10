package com.bizpilot.sales.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Spring Data JPA interface projection for {@link InvoiceRepository#findTopCustomersByRevenue}. */
public interface CustomerRevenueRow {

    UUID getCustomerId();

    String getCustomerName();

    BigDecimal getRevenue();
}
