package com.aicommandcenter.ai.command;

import java.util.Set;

/**
 * A controlled application tool.
 *
 * <p>Implementations are Spring beans, discovered by {@link CommandToolRegistry}. Each tool
 * declares the exact argument names it accepts and performs its own validation before calling a
 * service — the AI layer never reaches a repository directly.</p>
 */
public interface CommandTool {

    CommandIntent intent();

    /** Argument names this tool accepts; anything else is rejected before execution. */
    Set<String> allowedArguments();

    /** Runs against the authenticated user. Implementations must scope every lookup by {@code userId}. */
    ToolResult execute(Long userId, CommandArgs args);
}
