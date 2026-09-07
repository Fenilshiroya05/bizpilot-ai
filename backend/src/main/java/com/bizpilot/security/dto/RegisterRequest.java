package com.bizpilot.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        // 72: BCrypt only uses the first 72 bytes of its input; a longer value
        // wouldn't add real strength and (for multi-byte UTF-8 passwords) could
        // exceed BCryptPasswordEncoder's hard 72-byte limit and throw.
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String password,

        @NotBlank @Size(max = 100) String firstName,

        @NotBlank @Size(max = 100) String lastName
) {
}
