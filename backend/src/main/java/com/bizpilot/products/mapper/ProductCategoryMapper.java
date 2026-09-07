package com.bizpilot.products.mapper;

import com.bizpilot.products.dto.ProductCategoryResponse;
import com.bizpilot.products.entity.ProductCategory;
import org.springframework.stereotype.Component;

@Component
public class ProductCategoryMapper {

    public ProductCategoryResponse toResponse(ProductCategory category) {
        return new ProductCategoryResponse(
                category.getId(),
                category.getName(),
                category.getOrganization().getId(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
