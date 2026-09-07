package com.bizpilot.products.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductCategoryCreateRequest(
        @NotBlank @Size(max = 255) String name
) {
}
