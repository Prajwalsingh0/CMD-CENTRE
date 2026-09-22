package com.aicommandcenter.document.dto;

/** Extracted text plus an explicit flag telling the client it was cut short. */
public record DocumentContentResponse(
        Long id,
        String name,
        String text,
        boolean truncated,
        int totalChars) {
}
