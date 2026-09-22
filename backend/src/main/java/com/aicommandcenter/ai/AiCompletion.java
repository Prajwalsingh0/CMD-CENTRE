package com.aicommandcenter.ai;

/**
 * Provider-neutral completion result.
 *
 * @param text     generated text (never {@code null})
 * @param provider provider id that produced it
 * @param model    model id, or {@code "deterministic"} for the local provider
 * @param remote   whether the call left the process
 */
public record AiCompletion(String text, String provider, String model, boolean remote) {
}
