package com.bizpilot.sales.mapper;

import com.bizpilot.sales.dto.QuotationResponse;
import com.bizpilot.sales.dto.QuotationSummaryResponse;
import com.bizpilot.sales.entity.Quotation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuotationMapper {

    private final QuotationItemMapper itemMapper;

    public QuotationMapper(QuotationItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /** Only ever call on a {@code Quotation} whose {@code items} were eagerly fetched (see repository Javadoc). */
    public QuotationResponse toResponse(Quotation quotation) {
        List<com.bizpilot.sales.dto.QuotationItemResponse> items = quotation.getItems().stream()
                .map(itemMapper::toResponse)
                .toList();
        return new QuotationResponse(
                quotation.getId(),
                quotation.getCustomer().getId(),
                quotation.getStatus(),
                quotation.getValidUntil(),
                quotation.getDiscountPercentage(),
                quotation.getSubtotal(),
                quotation.getDiscountAmount(),
                quotation.getTaxAmount(),
                quotation.getGrandTotal(),
                items,
                quotation.getOrganization().getId(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt()
        );
    }

    /** Safe to call on any {@code Quotation}, loaded any way — never touches the lazy {@code items} collection. */
    public QuotationSummaryResponse toSummaryResponse(Quotation quotation) {
        return new QuotationSummaryResponse(
                quotation.getId(),
                quotation.getCustomer().getId(),
                quotation.getStatus(),
                quotation.getValidUntil(),
                quotation.getDiscountPercentage(),
                quotation.getSubtotal(),
                quotation.getDiscountAmount(),
                quotation.getTaxAmount(),
                quotation.getGrandTotal(),
                quotation.getOrganization().getId(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt()
        );
    }
}
