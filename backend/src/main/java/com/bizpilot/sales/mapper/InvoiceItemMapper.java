package com.bizpilot.sales.mapper;

import com.bizpilot.sales.dto.InvoiceItemResponse;
import com.bizpilot.sales.entity.InvoiceItem;
import org.springframework.stereotype.Component;

@Component
public class InvoiceItemMapper {

    public InvoiceItemResponse toResponse(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTaxPercentage(),
                item.getLineSubtotal(),
                item.getLineTaxAmount()
        );
    }
}
