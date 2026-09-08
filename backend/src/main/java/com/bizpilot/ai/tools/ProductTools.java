package com.bizpilot.ai.tools;

import com.bizpilot.ai.tools.dto.ProductToolResult;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.service.ProductSearchCriteria;
import com.bizpilot.products.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Read-only AI tools over the organization's product catalog (Phase 17,
 * CLAUDE.md §19) — mirrors {@link CustomerTools}'s exact structure/rationale.
 */
@Component
public class ProductTools {

    private static final Logger log = LoggerFactory.getLogger(ProductTools.class);

    private static final int MAX_RESULTS = 5;
    private static final int MAX_QUERY_LENGTH = 200;

    private final ProductService productService;

    public ProductTools(ProductService productService) {
        this.productService = productService;
    }

    @Tool(description = "Search the current organization's product catalog by name, SKU, or description. "
            + "Returns at most 5 matches.")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public List<ProductToolResult> searchProducts(
            @ToolParam(description = "Free-text search term (matched against name, SKU, and description)")
            String query) {
        Instant start = Instant.now();
        String normalized = normalizeQuery(query);
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null, normalized);
        Page<Product> page = productService.search(criteria, PageRequest.of(0, MAX_RESULTS));
        List<ProductToolResult> results = page.getContent().stream().map(ProductTools::toResult).toList();
        logOutcome("searchProducts", start, results.size());
        return results;
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank.");
        }
        String trimmed = query.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    private static void logOutcome(String toolName, Instant start, int resultCount) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("AI tool call succeeded [tool={}, resultCount={}, durationMs={}]", toolName, resultCount,
                durationMs);
    }

    private static ProductToolResult toResult(Product product) {
        return new ProductToolResult(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnit(),
                product.getPrice(),
                product.getTaxPercentage(),
                product.getStatus()
        );
    }
}
