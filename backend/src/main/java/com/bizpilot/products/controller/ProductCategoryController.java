package com.bizpilot.products.controller;

import com.bizpilot.products.dto.ProductCategoryCreateRequest;
import com.bizpilot.products.dto.ProductCategoryResponse;
import com.bizpilot.products.dto.ProductCategoryUpdateRequest;
import com.bizpilot.products.mapper.ProductCategoryMapper;
import com.bizpilot.products.service.ProductCategoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Product category management (CLAUDE.md §13). Reuses the {@code PRODUCT_*}
 * permissions — no dedicated category permission was introduced, per project
 * instructions §13 ("do not create new permissions unless absolutely
 * required"). Mounted under {@code /api/v1/products/categories} (a literal
 * path segment) — Spring MVC resolves this ahead of
 * {@code ProductController}'s {@code /api/v1/products/{id}} since exact path
 * segments always take precedence over path-variable segments; verified
 * with a live request in manual validation.
 */
@RestController
@RequestMapping("/api/v1/products/categories")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;
    private final ProductCategoryMapper categoryMapper;

    public ProductCategoryController(ProductCategoryService categoryService, ProductCategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ProductCategoryResponse> create(@Valid @RequestBody ProductCategoryCreateRequest request) {
        ProductCategoryResponse response = categoryMapper.toResponse(categoryService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/products/categories/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ProductCategoryResponse get(@PathVariable UUID id) {
        return categoryMapper.toResponse(categoryService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public Page<ProductCategoryResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return categoryService.list(pageable).map(categoryMapper::toResponse);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductCategoryResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody ProductCategoryUpdateRequest request) {
        return categoryMapper.toResponse(categoryService.update(id, request));
    }

    /**
     * Hard delete (project instructions §3/§15) — {@code products.category_id}
     * is {@code ON DELETE SET NULL}, so this never deletes or blocks on
     * referencing products; it only clears their category assignment.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
