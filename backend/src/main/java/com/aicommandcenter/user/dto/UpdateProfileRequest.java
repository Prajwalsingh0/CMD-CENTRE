package com.aicommandcenter.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 120) String displayName,
        @Size(max = 200) String headline) {
}
