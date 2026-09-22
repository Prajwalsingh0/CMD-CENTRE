package com.aicommandcenter.common;

import java.util.List;

/**
 * Standard error payload returned by every failing endpoint.
 * Never contains stack traces or internal details.
 */
public record ApiError(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldIssue> details) {

    public record FieldIssue(String field, String issue) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(java.time.Instant.now().toString(), status, error, message, path, List.of());
    }
}
