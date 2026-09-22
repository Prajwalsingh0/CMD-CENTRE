package com.aicommandcenter.ai.command;

import java.util.List;
import java.util.Map;

/**
 * Outcome of running a tool.
 *
 * @param summary one line describing what happened (written to the activity log and shown in the UI)
 * @param steps   the ordered checklist the UI renders, e.g. "Created goal", "Created 14 tasks"
 * @param data    payload for the client; DTO maps only, never entities
 */
public record ToolResult(String summary, List<String> steps, Map<String, Object> data) {

    public static ToolResult of(String summary, List<String> steps) {
        return new ToolResult(summary, steps, Map.of());
    }

    public static ToolResult of(String summary, List<String> steps, Map<String, Object> data) {
        return new ToolResult(summary, steps, data);
    }
}
