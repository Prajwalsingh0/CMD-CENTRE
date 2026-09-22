package com.aicommandcenter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SkillRequest(
        @NotBlank @Size(max = 80) String skill,
        @Size(max = 20)
        @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT", message = "level must be BEGINNER, INTERMEDIATE, ADVANCED or EXPERT")
        String level,
        Boolean verified) {
}
