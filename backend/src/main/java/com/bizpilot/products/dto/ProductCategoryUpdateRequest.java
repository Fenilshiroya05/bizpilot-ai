package com.bizpilot.products.dto;

import jakarta.validation.constraints.Size;

/** {@code null} means "leave unchanged" — same PATCH convention as every other update DTO in this project. */
public record ProductCategoryUpdateRequest(
        @Size(max = 255) String name
) {
}
