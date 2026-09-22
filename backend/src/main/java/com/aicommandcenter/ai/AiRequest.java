package com.aicommandcenter.ai;

import java.util.List;

/**
 * Provider-neutral completion request.
 *
 * @param system      system instruction; may be {@code null}
 * @param messages    conversation turns, oldest first; must not be empty
 * @param task        capability hint (see {@link AiTask})
 * @param jsonMode    ask the provider to constrain output to a JSON object
 * @param maxTokens   upper bound on generated tokens
 * @param temperature sampling temperature; 0 for deterministic tasks
 */
public record AiRequest(
        String system,
        List<AiMessage> messages,
        AiTask task,
        boolean jsonMode,
        int maxTokens,
        double temperature) {

    public AiRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("AiRequest requires at least one message");
        }
        if (messages.size() > 24) {
            throw new IllegalArgumentException("AiRequest is limited to 24 messages");
        }
    }

    public static AiRequest of(AiTask task, String system, String userMessage) {
        return new AiRequest(system, List.of(AiMessage.user(userMessage)), task, false, 1024, 0.2);
    }

    public static AiRequest json(AiTask task, String system, String userMessage) {
        return new AiRequest(system, List.of(AiMessage.user(userMessage)), task, true, 1024, 0.0);
    }

    public static AiRequest chat(String userMessage) {
        return new AiRequest(null, List.of(AiMessage.user(userMessage)), AiTask.GENERIC, false, 1024, 0.3);
    }

    public String lastUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return messages.get(i).content();
            }
        }
        return "";
    }
}
