package com.bizpilot.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeadNoteRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
