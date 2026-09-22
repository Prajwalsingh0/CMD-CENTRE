package com.aicommandcenter.ai.command;

import com.aicommandcenter.ai.AiException;
import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Asks a language model to choose an intent and fill in arguments.
 *
 * <p>The model is given a closed vocabulary (the {@link CommandIntent} enum and each intent's
 * declared arguments) and is required to answer with a JSON object. Everything it returns is then
 * re-checked against that vocabulary: an unknown intent degrades to {@link CommandIntent#UNKNOWN}
 * and any failure at all returns {@link Optional#empty()} so the caller falls back to the
 * deterministic parser. Model output is therefore never trusted — only re-validated.</p>
 */
@Component
public class LlmCommandParser {

    private static final Logger log = LoggerFactory.getLogger(LlmCommandParser.class);
    private static final int MAX_ARGUMENTS = 12;

    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public LlmCommandParser(AiService aiService, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    public Optional<ParsedCommand> parse(String command) {
        if (!aiService.supportsStructuredOutput()) {
            return Optional.empty();
        }
        try {
            String raw = aiService.complete(AiRequest.json(AiTask.GENERIC, systemPrompt(), command)).text();
            return Optional.of(read(raw));
        } catch (AiException ex) {
            log.info("LLM command parsing unavailable ({}); using the deterministic parser", ex.getCode());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("LLM command parsing produced an unusable result; using the deterministic parser");
            return Optional.empty();
        }
    }

    private ParsedCommand read(String raw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw AiException.invalidResponse(aiService.providerName());
        }
        CommandIntent intent = CommandIntent.fromString(root.path("intent").asText(null));
        if (intent == CommandIntent.UNKNOWN) {
            String rationale = root.path("rationale").asText("No supported action matched this command.");
            return ParsedCommand.unknown(rationale);
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        JsonNode node = root.path("arguments");
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (arguments.size() >= MAX_ARGUMENTS) {
                    return;
                }
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    return;
                }
                if (value.isArray()) {
                    StringBuilder joined = new StringBuilder();
                    value.forEach(item -> {
                        if (joined.length() > 0) {
                            joined.append(',');
                        }
                        joined.append(item.asText(""));
                    });
                    arguments.put(key, joined.toString());
                } else {
                    arguments.put(key, value.asText(""));
                }
            });
        }
        // Drop arguments the intent does not declare before the map ever reaches a tool.
        arguments.keySet().removeIf(key -> !intent.arguments().contains(key));
        String rationale = root.path("rationale").asText("Interpreted as " + intent.name() + ".");
        return new ParsedCommand(intent, arguments, trim(rationale), ParsedCommand.SOURCE_LLM);
    }

    private String systemPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("You convert a user's natural-language instruction into exactly one structured command.\n");
        builder.append("Reply with a single JSON object and nothing else, shaped like:\n");
        builder.append("{\"intent\":\"CREATE_TASK\",\"arguments\":{\"title\":\"...\"},\"rationale\":\"one short sentence\"}\n\n");
        builder.append("TODAY'S DATE: ").append(LocalDate.now()).append(". Resolve relative dates to yyyy-MM-dd.\n\n");
        builder.append("ALLOWED INTENTS AND THEIR ARGUMENTS:\n");
        for (CommandIntent intent : CommandIntent.executable()) {
            builder.append("- ").append(intent.name()).append(": ").append(intent.description())
                    .append(" | arguments: ")
                    .append(intent.arguments().isEmpty() ? "(none)" : String.join(", ", intent.arguments()))
                    .append('\n');
        }
        builder.append('\n');
        builder.append("Rules you must follow:\n");
        builder.append("1. Use only the intents above and only the listed argument names.\n");
        builder.append("2. Never invent an argument. Omit anything you cannot determine from the instruction.\n");
        builder.append("3. If nothing fits, use intent UNKNOWN with an empty arguments object.\n");
        builder.append("4. Never emit SQL, JSON paths, code or shell commands in any argument value.\n");
        builder.append("5. Titles must be concise (at most 180 characters).");
        return builder.toString();
    }

    private static String trim(String value) {
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
