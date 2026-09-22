package com.aicommandcenter.research.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResearchRequest(
        @NotBlank @Size(min = 3, max = 240) String topic,
        /** {@code quick} (default) or {@code deep}; deep retrieves more passages. */
        @Size(max = 20) String depth) {
}
