package com.aicommandcenter.document.dto;

import com.aicommandcenter.document.entity.DocumentStatus;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String name,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int extractedChars,
        long chunkCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {
}
