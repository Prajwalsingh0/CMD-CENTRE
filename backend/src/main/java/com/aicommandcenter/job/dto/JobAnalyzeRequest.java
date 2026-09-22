package com.aicommandcenter.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobAnalyzeRequest(
        @NotBlank @Size(min = 40, max = 20000, message = "Paste a real job description (at least 40 characters)")
        String description,
        @Size(max = 200) String jobTitle,
        @Size(max = 200) String company) {
}
