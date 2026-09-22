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
 *
 * <p>Branch order is deliberate and goes from most specific to least specific. Reporting verbs
 * ("show", "list", "what") are checked <em>before</em> noun keywords, otherwise "list my tasks"
 * would be read as a request to create a task.</p>
 */
@Component
public class HeuristicCommandParser {

    private static final Pattern QUOTED = Pattern.compile("[\"“”']([^\"“”']{2,180})[\"“”']");
    private static final Pattern TASK_TITLE = Pattern.compile(
            "(?:to-?dos?|tasks?|reminders?)\\s*(?:called|named|titled|to|for|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMIND = Pattern.compile("remind me to\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOAL_TITLE = Pattern.compile(
            "goal\\s*(?:called|named|titled|to|for|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOPIC = Pattern.compile(
            "research\\s*(?:on|about|into|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_ID = Pattern.compile("\\b(?:task|id)\\s*#?(\\d{1,9})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOC_NAME = Pattern.compile(
            "(?:document|file|resume|cv|pdf|notes?)\\s*(?:called|named|titled|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private static final List<String> REPORTING_PREFIXES = List.of(
            "show", "list", "display", "what are", "what's", "whats", "give me", "tell me", "how many");
    private static final List<String> ACTION_PREFIXES = List.of(
            "create", "add", "make", "new", "remind", "set up", "schedule", "plan", "start");

    public ParsedCommand parse(String command) {
        if (command == null || command.isBlank()) {
            return ParsedCommand.unknown("The command was empty");
        }
        String text = command.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String quoted = firstQuoted(text);
        boolean reporting = startsWithAny(lower, REPORTING_PREFIXES);
        boolean acting = startsWithAny(lower, ACTION_PREFIXES);

        // 1. Overdue reporting is unambiguous.
        if (lower.contains("overdue")) {
            return heuristic(CommandIntent.LIST_OVERDUE_TASKS, Map.of(),
                    "The command mentions overdue tasks");
        }

        // 2. Research.
        if (lower.startsWith("research") || lower.contains("research ")
                || lower.startsWith("find out about")) {
            String topic = capture(TOPIC, text);
            if (topic == null && quoted != null) {
                topic = quoted;
            }
            Map<String, String> args = new LinkedHashMap<>();
            args.put("topic", cleanTopic(topic == null ? stripActionPrefix(text) : topic));
            if (lower.contains("deep") || lower.contains("in depth") || lower.contains("detailed")) {
                args.put("depth", "deep");
            }
            return heuristic(CommandIntent.RESEARCH_TOPIC, args, "The command asks for research");
        }

        // 3. Document summarisation.
        if (lower.contains("summar") && containsAny(lower, "document", "resume", "cv", "pdf", "file", "notes")) {
            Map<String, String> args = new LinkedHashMap<>();
            String name = capture(DOC_NAME, text);
            if (name == null) {
                name = quoted;
            }
            if (name != null) {
                args.put("documentName", name.replaceAll("(?i)^(the|my|uploaded)\\s+", "")
                        .replaceAll("[.?!]$", "").trim());
            }
            return heuristic(CommandIntent.SUMMARIZE_DOCUMENT, args, "The command asks for a document summary");
        }

        // 4. Job-description analysis.
        if (containsAny(lower, "analyze", "analyse") && containsAny(lower, "job", "jd", "description", "role", "position")) {
            Map<String, String> args = new LinkedHashMap<>();
            String body = extractJobBody(text);
            if (body != null) {
                args.put("description", body);
            }
            return heuristic(CommandIntent.ANALYZE_JOB, args, "The command asks to analyse a job description");
        }

        // 5. Question against the user's own knowledge base.
        if (lower.startsWith("ask ") || containsAny(lower, "knowledge base", "my documents", "my notes", "my files")
                || (lower.endsWith("?") && containsAny(lower, "document", "notes", "resume", "file", "upload"))) {
            return heuristic(CommandIntent.ASK_KNOWLEDGE_BASE,
                    Map.of("question", text.replaceAll("(?i)^ask\\s*", "").trim()),
                    "The command asks a question against the knowledge base");
        }

        // 6. Multi-day plan.
        Integer horizon = DateHints.horizonDays(text);
        if (containsAny(lower, "plan", "roadmap", "schedule")
                && (horizon != null || containsAny(lower, "day", "week"))) {
            String focus = planFocus(text);
            Map<String, String> args = new LinkedHashMap<>();
            args.put("title", focus);
            args.put("focus", focus);
            args.put("days", String.valueOf(horizon == null ? 14 : horizon));
            return heuristic(CommandIntent.CREATE_GOAL_WITH_PLAN, args, "The command asks for a multi-day plan");
        }

        // 7. Statistics reporting.
        if (containsAny(lower, "stats", "statistics", "how am i doing", "productivity", "my progress",
                "summary of my work", "how many tasks")) {
            return heuristic(CommandIntent.SHOW_STATS, Map.of(), "The command asks for statistics");
        }

        // 8. Status changes. Guarded against imperative creation wording so
        //    "create a task to mark the invoice" is not read as a status update.
        if (!acting && containsAny(lower, "complete", "finish", "mark", "close", "done", "cancel")) {
            Map<String, String> args = new LinkedHashMap<>();
            Matcher id = TASK_ID.matcher(text);
            if (id.find()) {
                args.put("taskId", id.group(1));
            } else {
                String title = text.replaceAll("(?i)^.*?(?:complete|finish|mark|close|cancel)\\s*", "")
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

        // 9. Listing. Checked before noun keywords on purpose.
        if (reporting) {
            Map<String, String> args = new LinkedHashMap<>();
            if (lower.contains("in progress") || lower.contains("in-progress")) {
                args.put("status", "IN_PROGRESS");
            } else if (lower.contains("completed") || lower.contains("done")) {
                args.put("status", "COMPLETED");
            } else if (lower.contains("todo") || lower.contains("to do")) {
                args.put("status", "TODO");
            }
            if (lower.contains("high priority") || lower.contains("urgent")) {
                args.put("priority", "HIGH");
            }
            return heuristic(CommandIntent.LIST_TASKS, args, "The command asks to list tasks");
        }

        // 10. Goal creation.
        if (containsAny(lower, "goal", "objective", "target")) {
            String title = quoted != null ? quoted : capture(GOAL_TITLE, text);
            if (title == null) {
                title = text.replaceAll("(?i)^.*?goal\\s*", "");
            }
            Map<String, String> args = new LinkedHashMap<>();
            args.put("title", cleanup(title));
            String deadline = DateHints.resolve(text);
            if (deadline != null) {
                args.put("deadline", deadline);
            }
            return heuristic(CommandIntent.CREATE_GOAL, args, "The command asks to create a goal");
        }

        // 11. Task creation.
        if (containsAny(lower, "remind me", "task", "todo", "to-do", "to do")) {
            String title = capture(REMIND, text);
            if (title == null) {
                title = capture(TASK_TITLE, text);
            }
            if (title == null) {
                title = stripActionPrefix(text);
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

    private static boolean startsWithAny(String haystack, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (haystack.startsWith(prefix)) {
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

    private static String stripActionPrefix(String text) {
        return text.replaceAll("(?i)^\\s*(please\\s+)?(create|add|make|new|set up|schedule|start|research|find out about)\\s+(a\\s+|an\\s+|the\\s+)?", "")
                .trim();
    }

    private String planFocus(String text) {
        String focus = stripActionPrefix(text)
                .replaceAll("(?i)\\b\\d{1,3}\\s*[- ]?\\s*(day|week)s?\\b", "")
                .replaceAll("(?i)\\b(study|preparation|prep|learning|revision)?\\s*plan\\b.*$", "")
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
        Matcher quotedMatcher = QUOTED.matcher(text);
        if (quotedMatcher.find() && quotedMatcher.group(1).length() > 60) {
            return quotedMatcher.group(1).trim();
        }
        String withoutInstruction = text.replaceAll(
                "(?i)^\\s*(please\\s+)?(analyze|analyse|review|check)\\s+(this\\s+)?(job\\s+)?(description|jd|role|position)\\s*[:\\-]?\\s*",
                "").trim();
        return withoutInstruction.length() >= 40 ? withoutInstruction : null;
    }

    private static String stripTemporal(String value) {
        return value.replaceAll("(?i)\\b(today|tomorrow|tonight|next week|next month|"
                + "in \\d{1,3} (days?|weeks?)|on (monday|tuesday|wednesday|thursday|friday|saturday|sunday)|"
                + "by (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b", "")
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

    /** Exposed so the UI can show real examples that the parser genuinely handles. */
    public static List<String> supportedExamples() {
        return List.of(
                "Create a task to study Spring Boot tomorrow",
                "Create a 14-day Java interview preparation plan",
                "Create a goal called \"Get Java Backend Job\"",
                "Show me tasks that are overdue",
                "Summarize my uploaded resume",
                "Research Spring Boot security best practices",
                "Analyse this job description: <paste the text>",
                "Show my stats");
    }
}
