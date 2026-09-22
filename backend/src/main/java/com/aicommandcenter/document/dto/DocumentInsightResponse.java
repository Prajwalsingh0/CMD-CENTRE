package com.aicommandcenter.document.dto;

import java.time.Instant;

public record DocumentInsightResponse(
        Long documentId,
        String documentName,
        String operation,
        String content,
        String provider,
        Instant generatedAt) {
}
