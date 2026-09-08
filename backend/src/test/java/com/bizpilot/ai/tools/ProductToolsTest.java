package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.ProductToolResult;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.service.ProductSearchCriteria;
import com.bizpilot.products.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductToolsTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductTools tools = new ProductTools(productService);

    @Test
    void searchProductsDelegatesToProductServiceWithAFixedPageSizeOfFive() {
        Product product = product("Widget");
        when(productService.search(any(ProductSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        List<ProductToolResult> results = tools.searchProducts("widget");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Widget");
        verify(productService).search(
                eq(new ProductSearchCriteria(null, null, null, "widget")),
                eq(PageRequest.of(0, 5)));
    }

    @Test
    void searchProductsRejectsABlankQuery() {
        assertThatThrownBy(() -> tools.searchProducts("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchProductsTruncatesAnExcessivelyLongQuery() {
        String tooLong = "b".repeat(500);
        when(productService.search(any(ProductSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        tools.searchProducts(tooLong);

        verify(productService).search(
                eq(new ProductSearchCriteria(null, null, null, "b".repeat(200))),
                any(Pageable.class));
    }

    private static Product product(String name) {
        Organization organization = new Organization("Test Org");
        return new Product(organization, null, "SKU-1", name, "A description", "unit",
                BigDecimal.TEN, BigDecimal.ZERO);
    }
}
