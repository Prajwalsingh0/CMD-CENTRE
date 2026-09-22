package com.aicommandcenter.ai.activity;

import java.time.Instant;

public record AiActivityResponse(
        Long id,
        String command,
        String intent,
        String status,
        String resultSummary,
        Instant createdAt) {
}
