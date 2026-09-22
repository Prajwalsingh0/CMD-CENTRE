package com.aicommandcenter.ai.command;

import java.util.List;
import java.util.Map;

/** API response for {@code POST /api/ai/command}. */
public record CommandResultResponse(
        Long activityId,
        String command,
        String intent,
        String status,
        String summary,
        List<String> steps,
        Map<String, Object> data,
        String parsedBy,
        String rationale,
        String provider) {
}
