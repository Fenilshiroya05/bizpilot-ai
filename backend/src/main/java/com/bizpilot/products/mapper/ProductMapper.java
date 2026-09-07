package com.bizpilot.products.mapper;

import com.bizpilot.products.dto.ProductResponse;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.entity.ProductCategory;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        ProductCategory category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnit(),
                product.getPrice(),
                product.getTaxPercentage(),
                product.getStatus(),
                category == null ? null : category.getId(),
                product.getOrganization().getId(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
