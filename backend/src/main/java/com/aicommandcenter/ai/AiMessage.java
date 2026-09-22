package com.aicommandcenter.ai;

/** One chat message. Roles are limited to the three the providers actually support. */
public record AiMessage(String role, String content) {

    public static AiMessage system(String content) {
        return new AiMessage("system", content);
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content);
    }
}
