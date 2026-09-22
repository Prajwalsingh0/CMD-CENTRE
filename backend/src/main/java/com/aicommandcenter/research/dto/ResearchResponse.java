package com.aicommandcenter.research.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param grounded whether any source was actually retrieved. When {@code false} the disclosure
 *                 field explains exactly why, and the client renders that instead of pretending
 *                 the report is referenced research.
 */
public record ResearchResponse(
        Long id,
        String topic,
        String overview,
        List<String> keyFindings,
        List<String> concepts,
        List<String> recommendations,
        List<String> sources,
        String summary,
        boolean grounded,
        String disclosure,
        String provider,
        Instant createdAt) {
}
