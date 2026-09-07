package com.bizpilot.sales.mapper;

import com.bizpilot.sales.dto.InvoiceItemResponse;
import com.bizpilot.sales.dto.InvoiceResponse;
import com.bizpilot.sales.dto.InvoiceSummaryResponse;
import com.bizpilot.sales.entity.Invoice;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoiceMapper {

    private final InvoiceItemMapper itemMapper;

    public InvoiceMapper(InvoiceItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /** Only ever call on an {@code Invoice} whose {@code items} were eagerly fetched (see repository Javadoc). */
    public InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(itemMapper::toResponse)
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCustomer().getId(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotal(),
                items,
                invoice.getOrganization().getId(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }

    /** Safe to call on any {@code Invoice}, loaded any way — never touches the lazy {@code items} collection. */
    public InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getCustomer().getId(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotal(),
                invoice.getOrganization().getId(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
