package com.aicommandcenter.notification.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String eventType,
        String title,
        String message,
        boolean read,
        Instant createdAt) {
}
