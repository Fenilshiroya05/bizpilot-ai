package com.bizpilot.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerNoteRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
