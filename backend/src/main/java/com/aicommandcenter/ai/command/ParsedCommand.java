package com.aicommandcenter.ai.command;

import java.util.Map;

/**
 * What the parser understood.
 *
 * @param intent    one of the whitelisted intents, or {@link CommandIntent#UNKNOWN}
 * @param arguments flat map of primitive arguments, re-validated by the tool that consumes it
 * @param rationale one short sentence explaining the decision — shown to the user, never trusted
 * @param source    {@code llm} when a language model produced it, {@code heuristic} otherwise
 */
public record ParsedCommand(
        CommandIntent intent,
        Map<String, String> arguments,
        String rationale,
        String source) {

    public static final String SOURCE_LLM = "llm";
    public static final String SOURCE_HEURISTIC = "heuristic";

    public static ParsedCommand unknown(String rationale) {
        return new ParsedCommand(CommandIntent.UNKNOWN, Map.of(), rationale, SOURCE_HEURISTIC);
    }

    public boolean isExecutable() {
        return intent != CommandIntent.UNKNOWN;
    }
}
