package com.bizpilot.sales.mapper;

import com.bizpilot.sales.dto.QuotationItemResponse;
import com.bizpilot.sales.entity.QuotationItem;
import org.springframework.stereotype.Component;

@Component
public class QuotationItemMapper {

    public QuotationItemResponse toResponse(QuotationItem item) {
        return new QuotationItemResponse(
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
