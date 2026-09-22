package com.aicommandcenter.ai.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The whitelist itself.
 *
 * <p>Tools are discovered from the Spring context, and the registry is the only way to reach one.
 * An intent with no registered tool is not executable — which is why adding a new AI capability
 * requires writing a tool rather than teaching the model a new trick.</p>
 */
@Component
public class CommandToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandToolRegistry.class);

    private final Map<CommandIntent, CommandTool> tools = new EnumMap<>(CommandIntent.class);

    public CommandToolRegistry(List<CommandTool> discovered) {
        for (CommandTool tool : discovered) {
            CommandTool previous = tools.put(tool.intent(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate command tool for intent " + tool.intent());
            }
        }
        if (tools.containsKey(CommandIntent.UNKNOWN)) {
            throw new IllegalStateException("UNKNOWN must never have a command tool");
        }
        log.info("AI command tools registered: {}", tools.keySet());
    }

    public Optional<CommandTool> find(CommandIntent intent) {
        return Optional.ofNullable(tools.get(intent));
    }

    public boolean supports(CommandIntent intent) {
        return tools.containsKey(intent);
    }

    public Set<CommandIntent> supportedIntents() {
        return Set.copyOf(tools.keySet());
    }
}
