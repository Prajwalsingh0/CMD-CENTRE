package com.aicommandcenter.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A single chat message.
 *
 * <p>There is deliberately no conversation-history field: the service answers each message from the
 * user's own documents plus the message itself, so history would be dead input that only widened
 * the request surface.</p>
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String message) {
}
