package com.aicommandcenter.user.dto;

import java.time.Instant;
import java.util.List;

/** Public representation of a user. Deliberately has no password field at all. */
public record UserResponse(
        Long id,
        String email,
        String displayName,
        String headline,
        Instant createdAt,
        List<SkillResponse> skills) {
}
