package com.aicommandcenter.ai.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicCommandParserTest {

    private final HeuristicCommandParser parser = new HeuristicCommandParser();

    @Test
    @DisplayName("task creation commands keep the title and resolve the date")
    void taskCommands() {
        ParsedCommand parsed = parser.parse("Create a task to study Spring Boot tomorrow");
        assertThat(parsed.intent()).isEqualTo(CommandIntent.CREATE_TASK);
        assertThat(parsed.arguments().get("title")).containsIgnoringCase("study Spring Boot");
        assertThat(parsed.arguments().get("dueDate")).isEqualTo(java.time.LocalDate.now().plusDays(1).toString());
        assertThat(parsed.source()).isEqualTo("heuristic");

        ParsedCommand reminder = parser.parse("Remind me to pay the electricity bill on Friday");
        assertThat(reminder.intent()).isEqualTo(CommandIntent.CREATE_TASK);
        assertThat(reminder.arguments().get("title")).containsIgnoringCase("pay the electricity bill");
    }

    @Test
    @DisplayName("priority words are turned into a priority argument")
    void priorityWords() {
        ParsedCommand parsed = parser.parse("Create an urgent task to fix the outage");
        assertThat(parsed.intent()).isEqualTo(CommandIntent.CREATE_TASK);
        assertThat(parsed.arguments().get("priority")).isEqualTo("URGENT");
    }

    @Test
    @DisplayName("plan commands carry a bounded horizon")
    void planCommands() {
        ParsedCommand parsed = parser.parse("Create a 14-day Java interview preparation plan");
        assertThat(parsed.intent()).isEqualTo(CommandIntent.CREATE_GOAL_WITH_PLAN);
        assertThat(parsed.arguments().get("days")).isEqualTo("14");
        assertThat(parsed.arguments().get("focus")).containsIgnoringCase("java");
    }

    @Test
    @DisplayName("goal commands read quoted and unquoted titles")
    void goalCommands() {
        ParsedCommand quoted = parser.parse("Create a goal called \"Get Java Backend Job\"");
        assertThat(quoted.intent()).isEqualTo(CommandIntent.CREATE_GOAL);
        assertThat(quoted.arguments().get("title")).isEqualTo("Get Java Backend Job");

        ParsedCommand plain = parser.parse("Create a goal to run a half marathon");
        assertThat(plain.intent()).isEqualTo(CommandIntent.CREATE_GOAL);
        assertThat(plain.arguments().get("title")).containsIgnoringCase("half marathon");
    }

    @Test
    @DisplayName("reporting commands are classified without arguments they cannot have")
    void reportingCommands() {
        assertThat(parser.parse("Show me tasks that are overdue").intent())
                .isEqualTo(CommandIntent.LIST_OVERDUE_TASKS);
        assertThat(parser.parse("Show my stats").intent()).isEqualTo(CommandIntent.SHOW_STATS);
        assertThat(parser.parse("List my tasks in progress").intent()).isEqualTo(CommandIntent.LIST_TASKS);
        assertThat(parser.parse("List my tasks in progress").arguments().get("status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("status changes carry an id or a title")
    void statusCommands() {
        ParsedCommand byId = parser.parse("Complete task 42");
        assertThat(byId.intent()).isEqualTo(CommandIntent.UPDATE_TASK_STATUS);
        assertThat(byId.arguments().get("taskId")).isEqualTo("42");
        assertThat(byId.arguments().get("status")).isEqualTo("COMPLETED");

        ParsedCommand byTitle = parser.parse("Mark the schema review as done");
        assertThat(byTitle.intent()).isEqualTo(CommandIntent.UPDATE_TASK_STATUS);
        assertThat(byTitle.arguments()).containsKey("taskTitle");
    }

    @Test
    @DisplayName("document, research, job and knowledge commands are recognised")
    void featureCommands() {
        assertThat(parser.parse("Summarize my uploaded resume").intent())
                .isEqualTo(CommandIntent.SUMMARIZE_DOCUMENT);
        assertThat(parser.parse("Research Spring Boot security best practices").intent())
                .isEqualTo(CommandIntent.RESEARCH_TOPIC);
        assertThat(parser.parse("Research Spring Boot security best practices").arguments().get("topic"))
                .containsIgnoringCase("spring boot");
        assertThat(parser.parse("Analyze this job description").intent()).isEqualTo(CommandIntent.ANALYZE_JOB);
        assertThat(parser.parse("Ask my documents about the deployment checklist").intent())
                .isEqualTo(CommandIntent.ASK_KNOWLEDGE_BASE);
    }

    @Test
    @DisplayName("nonsense is UNKNOWN with a helpful message rather than a guess")
    void unknownCommands() {
        ParsedCommand parsed = parser.parse("Make me a sandwich");
        assertThat(parsed.intent()).isEqualTo(CommandIntent.UNKNOWN);
        assertThat(parsed.isExecutable()).isFalse();
        assertThat(parsed.rationale()).isNotEmpty();

        assertThat(parser.parse("").intent()).isEqualTo(CommandIntent.UNKNOWN);
        assertThat(parser.parse(null).intent()).isEqualTo(CommandIntent.UNKNOWN);
    }

    @Test
    @DisplayName("every intent the parser can emit names only declared arguments")
    void parserNeverInventsArguments() {
        String[] commands = {
                "Create a task to study Spring Boot tomorrow",
                "Create a 14-day Java plan",
                "Create a goal called \"Ship it\"",
                "Show me tasks that are overdue",
                "Show my stats",
                "Complete task 7",
                "Summarize my resume",
                "Research Kafka exactly once semantics",
                "Analyze this job description",
                "Ask my documents about indexes",
                "Show my tasks"
        };
        for (String command : commands) {
            ParsedCommand parsed = parser.parse(command);
            if (!parsed.isExecutable()) {
                continue;
            }
            assertThat(parsed.arguments().keySet())
                    .as(command)
                    .isSubsetOf(parsed.intent().arguments());
        }
    }
}
