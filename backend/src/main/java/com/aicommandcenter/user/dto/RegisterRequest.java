package com.aicommandcenter.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank
        @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "password must contain at least one letter")
        @Pattern(regexp = ".*[0-9].*", message = "password must contain at least one digit")
        String password,
        @NotBlank @Size(max = 120) String displayName) {
}
