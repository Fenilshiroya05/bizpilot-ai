package com.bizpilot.products.controller;

import com.bizpilot.products.dto.ProductCreateRequest;
import com.bizpilot.products.dto.ProductResponse;
import com.bizpilot.products.dto.ProductUpdateRequest;
import com.bizpilot.products.entity.ProductStatus;
import com.bizpilot.products.mapper.ProductMapper;
import com.bizpilot.products.service.ProductSearchCriteria;
import com.bizpilot.products.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Product catalog management (CLAUDE.md §13). Every endpoint requires the
 * matching {@code PRODUCT_*} permission (added in this phase — see
 * docs/security.md); the organization is always resolved server-side from
 * the authenticated tenant context inside {@code ProductService} — no
 * endpoint here accepts an organization id from the client in any form.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse response = productMapper.toResponse(productService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ProductResponse get(@PathVariable UUID id) {
        return productMapper.toResponse(productService.getById(id));
    }

    /**
     * Listing/search/filter/pagination in one endpoint (project instructions
     * §17). {@code q} performs a case-insensitive partial match against
     * SKU/name/description. Unlike Customer/Lead, {@code status} has no
     * "excluded by default" value — {@code INACTIVE} is a normal business
     * state, not an archive state, so omitting {@code status} returns
     * products of every status. Sorting/pagination use Spring Data's
     * standard {@code page}/{@code size}/{@code sort} parameters, applied at
     * the database level.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public Page<ProductResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String unit,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(status, categoryId, unit, q);
        return productService.search(criteria, pageable).map(productMapper::toResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        return productMapper.toResponse(productService.update(id, request));
    }

    /**
     * CLAUDE.md's "CRUD" delete, implemented as a transition to
     * {@link ProductStatus#INACTIVE} — see {@code ProductService#delete}'s
     * Javadoc for why no separate archive mechanism was introduced.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
