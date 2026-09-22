package com.aicommandcenter.ai.command;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The complete set of things the AI command bar can be asked to do.
 *
 * <p>This enum <em>is</em> the security boundary. A language model's only influence over the
 * system is choosing one of these constants and filling in a flat map of primitive strings;
 * there is no code path from model output to SQL, to an entity, or to a repository. Adding a
 * capability requires adding a tool, and every tool re-validates its own arguments.</p>
 */
public enum CommandIntent {

    CREATE_TASK("Create a single task",
            Set.of("title", "description", "dueDate", "priority", "goalTitle", "tags")),
    CREATE_GOAL("Create a goal",
            Set.of("title", "description", "deadline", "priority")),
    CREATE_GOAL_WITH_PLAN("Create a goal together with a day-by-day task plan",
            Set.of("title", "days", "focus", "deadline", "priority")),
    LIST_TASKS("List the user's tasks, optionally filtered",
            Set.of("status", "priority", "limit", "search")),
    LIST_OVERDUE_TASKS("List tasks that are past their due date", Set.of()),
    UPDATE_TASK_STATUS("Change the status of an existing task",
            Set.of("taskId", "taskTitle", "status")),
    SUMMARIZE_DOCUMENT("Summarise an uploaded document",
            Set.of("documentName", "documentId")),
    ASK_KNOWLEDGE_BASE("Answer a question from the user's own documents",
            Set.of("question")),
    ANALYZE_JOB("Analyse a pasted job description",
            Set.of("description", "jobTitle", "company")),
    RESEARCH_TOPIC("Produce a research report on a topic",
            Set.of("topic", "depth")),
    SHOW_STATS("Report productivity statistics", Set.of()),
    UNKNOWN("The command could not be mapped to a supported action", Set.of());

    private final String description;
    private final Set<String> arguments;

    CommandIntent(String description, Set<String> arguments) {
        this.description = description;
        this.arguments = arguments;
    }

    public String description() {
        return description;
    }

    public Set<String> arguments() {
        return arguments;
    }

    /** Intents that actually do something; {@link #UNKNOWN} is excluded. */
    public static Set<CommandIntent> executable() {
        Set<CommandIntent> intents = new LinkedHashSet<>(Set.of(values()));
        intents.remove(UNKNOWN);
        return intents;
    }

    public static CommandIntent fromString(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String normalised = raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (CommandIntent intent : values()) {
            if (intent.name().equals(normalised)) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
