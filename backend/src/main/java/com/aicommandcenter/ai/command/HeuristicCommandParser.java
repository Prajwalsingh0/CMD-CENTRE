package com.aicommandcenter.ai.command;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic command parser.
 *
 * <p>It is the default parser (and the fallback whenever a language model is unavailable or
 * returns something unusable), which means the command bar is fully functional with no
 * credentials. It is intentionally pattern-based: every branch is inspectable and unit-testable,
 * and none of them can produce an argument the tools do not expect.</p>
 */
@Component
public class HeuristicCommandParser {

    private static final Pattern QUOTED = Pattern.compile("[\"“”']([^\"“”']{2,180})[\"“”']");
    private static final Pattern TASK_TITLE = Pattern.compile(
            "(?:task|todo|to-do|reminder)\\s*(?:called|named|titled|to|for|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMIND = Pattern.compile(
            "remind me to\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOAL_TITLE = Pattern.compile(
            "goal\\s*(?:called|named|titled|to|for|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOPIC = Pattern.compile(
            "research\\s*(?:on|about|into|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_ID = Pattern.compile("\\b(?:task|id)\\s*#?(\\d{1,9})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOC_NAME = Pattern.compile(
            "(?:document|file|resume|cv|pdf|notes?)\\s*(?:called|named|titled|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    public ParsedCommand parse(String command) {
        if (command == null || command.isBlank()) {
            return ParsedCommand.unknown("The command was empty");
        }
        String text = command.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String quoted = firstQuoted(text);

        if (lower.contains("overdue")) {
            return heuristic(CommandIntent.LIST_OVERDUE_TASKS, Map.of(),
                    "The command mentions overdue tasks");
        }
        if (lower.startsWith("research") || lower.contains("research ")
                || lower.startsWith("find out about") || lower.contains("do a research")) {
            String topic = capture(TOPIC, text);
            if (topic == null && quoted != null) {
                topic = quoted;
            }
            Map<String, String> args = new LinkedHashMap<>();
            args.put("topic", cleanTopic(topic == null ? text : topic));
            if (lower.contains("deep") || lower.contains("in depth") || lower.contains("detailed")) {
                args.put("depth", "deep");
            }
            return heuristic(CommandIntent.RESEARCH_TOPIC, args, "The command asks for research");
        }
        if (lower.contains("summar") && containsAny(lower, "document", "resume", "cv", "pdf", "file", "notes")) {
            Map<String, String> args = new LinkedHashMap<>();
            String name = capture(DOC_NAME, text);
            if (name == null) {
                name = quoted;
            }
            if (name != null) {
                args.put("documentName", name.replaceAll("(?i)^(the|my)\\s+", "").replaceAll("[.?!]$", "").trim());
            }
            return heuristic(CommandIntent.SUMMARIZE_DOCUMENT, args, "The command asks for a document summary");
        }
        if (containsAny(lower, "analyze", "analyse") && containsAny(lower, "job", "jd", "description", "role", "position")) {
            Map<String, String> args = new LinkedHashMap<>();
            String body = extractJobBody(text);
            if (body != null) {
                args.put("description", body);
            }
            return heuristic(CommandIntent.ANALYZE_JOB, args, "The command asks to analyse a job description");
        }
        if (lower.startsWith("ask ") || containsAny(lower, "knowledge base", "my documents", "my notes", "my files")
                || (lower.endsWith("?") && containsAny(lower, "document", "notes", "resume", "file"))) {
            return heuristic(CommandIntent.ASK_KNOWLEDGE_BASE,
                    Map.of("question", text.replaceAll("(?i)^ask\\s*", "").trim()),
                    "The command asks a question against the knowledge base");
        }
        Integer horizon = DateHints.horizonDays(text);
        if (containsAny(lower, "plan", "roadmap", "schedule", "study plan", "preparation plan")
                && (horizon != null || containsAny(lower, "day", "week"))) {
            String focus = planFocus(text);
            return heuristic(CommandIntent.CREATE_GOAL_WITH_PLAN, new LinkedHashMap<>(Map.of(
                    "title", focus,
                    "focus", focus,
                    "days", String.valueOf(horizon == null ? 14 : horizon))),
                    "The command asks for a multi-day plan");
        }
        if (containsAny(lower, "goal", "objective", "target")) {
            String title = quoted != null ? quoted : capture(GOAL_TITLE, text);
            if (title == null) {
                title = text.replaceAll("(?i)^.*?goal\\s*", "").trim();
            }
            Map<String, String> args = new LinkedHashMap<>();
            args.put("title", cleanup(title));
            String deadline = DateHints.resolve(text);
            if (deadline != null) {
                args.put("deadline", deadline);
            }
            return heuristic(CommandIntent.CREATE_GOAL, args, "The command asks to create a goal");
        }
        if (containsAny(lower, "remind me", "task", "todo", "to-do", "to do")) {
            String title = capture(REMIND, text);
            if (title == null) {
                title = capture(TASK_TITLE, text);
            }
            if (title == null) {
                title = text;
            }
            Map<String, String> args = new LinkedHashMap<>();
            args.put("title", cleanup(stripTemporal(title)));
            String due = DateHints.resolve(text);
            if (due != null) {
                args.put("dueDate", due);
            }
            if (lower.contains("urgent") || lower.contains("critical")) {
                args.put("priority", "URGENT");
            } else if (lower.contains("high priority")) {
                args.put("priority", "HIGH");
            } else if (lower.contains("low priority")) {
                args.put("priority", "LOW");
            }
            return heuristic(CommandIntent.CREATE_TASK, args, "The command asks to create a task");
        }
        if (containsAny(lower, "complete", "finish", "mark", "done", "close")) {
            Map<String, String> args = new LinkedHashMap<>();
            Matcher id = TASK_ID.matcher(text);
            if (id.find()) {
                args.put("taskId", id.group(1));
            } else {
                String title = text.replaceAll("(?i)^.*?(?:complete|finish|mark|close)\\s*", "")
                        .replaceAll("(?i)\\s*(?:as\\s+)?(?:done|complete[d]?|finished)?\\s*$", "").trim();
                if (!title.isBlank()) {
                    args.put("taskTitle", title);
                }
            }
            if (lower.contains("in progress") || lower.contains("in-progress") || lower.contains("start")) {
                args.put("status", "IN_PROGRESS");
            } else if (lower.contains("cancel")) {
                args.put("status", "CANCELLED");
            } else {
                args.put("status", "COMPLETED");
            }
            return heuristic(CommandIntent.UPDATE_TASK_STATUS, args, "The command asks to change a task status");
        }
        if (lower.startsWith("show") || lower.startsWith("list") || lower.startsWith("what are")
                || lower.startsWith("what's") || lower.startsWith("give me")) {
            Map<String, String> args = new LinkedHashMap<>();
            if (lower.contains("in progress") || lower.contains("in-progress")) {
                args.put("status", "IN_PROGRESS");
            } else if (lower.contains("completed") || lower.contains("done")) {
                args.put("status", "COMPLETED");
            } else if (lower.contains("todo") || lower.contains("to do")) {
                args.put("status", "TODO");
            }
            if (containsAny(lower, "goal")) {
                return heuristic(CommandIntent.CREATE_GOAL, Map.of(), "No goal title was supplied");
            }
            return heuristic(CommandIntent.LIST_TASKS, args, "The command asks to list tasks");
        }
        if (containsAny(lower, "stats", "statistics", "how am i doing", "productivity", "my progress",
                "summary of my work")) {
            return heuristic(CommandIntent.SHOW_STATS, Map.of(), "The command asks for statistics");
        }
        return ParsedCommand.unknown(
                "No supported action matched this command. Try: create a task, create a goal, "
                        + "create a 14-day plan, list tasks, show overdue tasks, summarize a document, "
                        + "research a topic, or show my stats.");
    }

    private ParsedCommand heuristic(CommandIntent intent, Map<String, String> arguments, String rationale) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        arguments.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                cleaned.put(key, value.trim());
            }
        });
        return new ParsedCommand(intent, cleaned, rationale, ParsedCommand.SOURCE_HEURISTIC);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String capture(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String firstQuoted(String text) {
        Matcher matcher = QUOTED.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String planFocus(String text) {
        String focus = text
                .replaceAll("(?i)^\\s*(please\\s+)?(create|make|build|generate|give me)\\s+", "")
                .replaceAll("(?i)a\\s+\\d{1,3}\\s*[- ]?\\s*(day|week)s?\\s+", "")
                .replaceAll("(?i)\\d{1,3}\\s*[- ]?\\s*(day|week)s?\\s+", "")
                .replaceAll("(?i)(study|preparation|prep|learning|revision)?\\s*plan.*$", "")
                .replaceAll("[.?!]$", "")
                .trim();
        if (focus.isBlank() || focus.length() < 3) {
            focus = "Personal improvement plan";
        }
        return focus.length() > 160 ? focus.substring(0, 160) : focus;
    }

    private String extractJobBody(String text) {
        int newline = text.indexOf('\n');
        if (newline > 0 && text.length() - newline > 60) {
            return text.substring(newline + 1).trim();
        }
        Matcher quoted = QUOTED.matcher(text);
        if (quoted.find() && quoted.group(1).length() > 60) {
            return quoted.group(1).trim();
        }
        String withoutInstruction = text.replaceAll(
                "(?i)^\\s*(please\\s+)?(analyze|analyse|review|check)\\s+(this\\s+)?(job\\s+)?(description|jd|role|position)\\s*[:\\-]?\\s*",
                "").trim();
        return withoutInstruction.length() >= 40 ? withoutInstruction : null;
    }

    private static String stripTemporal(String value) {
        return value.replaceAll("(?i)\\b(today|tomorrow|tonight|next week|next month|"
                + "in \\d{1,3} (days?|weeks?)|on (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b", "")
                .replaceAll("\\s{2,}", " ").trim();
    }

    private static String cleanup(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("(?i)^(the|my|a|an)\\s+", "")
                .replaceAll("[.?!]+$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    private static String cleanTopic(String value) {
        String cleaned = value.replaceAll("(?i)^(on|about|into|the|topic)\\s+", "")
                .replaceAll("[.?!]+$", "")
                .trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    /** Exposed so the LLM parser can share the same post-processing rules. */
    public static List<String> supportedExamples() {
        return List.of(
                "Create a task to study Spring Boot tomorrow",
                "Create a 14-day Java interview preparation plan",
                "Create a goal called \"Get Java Backend Job\"",
                "Show me tasks that are overdue",
                "Summarize my uploaded resume",
                "Research Spring Boot security best practices",
                "Analyse this job description: <paste>",
                "Show my stats");
    }
}
