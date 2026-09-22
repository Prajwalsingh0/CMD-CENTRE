package com.aicommandcenter.ai;

import com.aicommandcenter.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end behaviour of the AI command bar.
 *
 * <p>These tests run against the default {@code local} provider, which is exactly the point: the
 * command system must be fully functional and fully verifiable without any external service.</p>
 */
class AiCommandIT extends IntegrationTestBase {

    @Test
    @DisplayName("a natural-language task command really creates the task")
    void createTaskCommand() throws Exception {
        String token = newUserToken();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        post("/api/ai/command", token, java.util.Map.of(
                "command", "Create a task to study Spring Boot tomorrow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.intent").value("CREATE_TASK"))
                .andExpect(jsonPath("$.activityId").isNumber())
                .andExpect(jsonPath("$.steps").isArray());

        // A single search token is used deliberately: MockMvc treats the URL as a template and
        // would re-encode a literal "%20", which is a harness detail rather than product behaviour.
        get("/api/tasks?search=study", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("study Spring Boot"))
                .andExpect(jsonPath("$.items[0].dueDate").value(tomorrow))
                .andExpect(jsonPath("$.items[0].status").value("TODO"));
    }

    @Test
    @DisplayName("a 14-day plan command creates a goal plus fourteen dated tasks")
    void planCommandCreatesRealWork() throws Exception {
        String token = newUserToken();

        var result = bodyOf(post("/api/ai/command", token, java.util.Map.of(
                "command", "Create a 14-day Java interview preparation plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.intent").value("CREATE_GOAL_WITH_PLAN"))
                .andExpect(jsonPath("$.steps.length()").value(3)));

        long goalId = result.path("data").path("goal").path("id").asLong();
        assertThat(goalId).isPositive();

        get("/api/tasks?goalId=" + goalId + "&size=100", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(14));

        get("/api/goals/" + goalId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(14))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("overdue and stats commands read the user's real data")
    void reportingCommands() throws Exception {
        String token = newUserToken();
        createTask(token, "Chase the invoice", LocalDate.now().minusDays(2).toString(), null);

        post("/api/ai/command", token, java.util.Map.of("command", "Show me tasks that are overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LIST_OVERDUE_TASKS"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        post("/api/ai/command", token, java.util.Map.of("command", "Show my stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("SHOW_STATS"))
                .andExpect(jsonPath("$.data.analytics.overdueTasks").value(1));
    }

    @Test
    @DisplayName("an unsupported command is rejected and writes nothing")
    void unsupportedCommandChangesNothing() throws Exception {
        String token = newUserToken();
        long before = bodyOf(get("/api/tasks?size=100", token)).path("totalItems").asLong();

        post("/api/ai/command", token, java.util.Map.of("command", "Order me a pizza and a coffee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.intent").value("UNKNOWN"))
                .andExpect(jsonPath("$.summary").isNotEmpty());

        long after = bodyOf(get("/api/tasks?size=100", token)).path("totalItems").asLong();
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("an empty command is refused as a bad request, not as an AI failure")
    void emptyCommandIsAValidationError() throws Exception {
        String token = newUserToken();
        post("/api/ai/command", token, java.util.Map.of("command", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a tool whose required argument is missing is rejected with a readable reason")
    void missingArgumentIsReportedClearly() throws Exception {
        String token = newUserToken();
        // "Analyse this job description" carries no description, so the tool must refuse.
        post("/api/ai/command", token, java.util.Map.of("command", "Analyze this job description."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("ANALYZE_JOB"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    @Test
    @DisplayName("every command is written to the activity trail")
    void activityTrailIsComplete() throws Exception {
        String token = newUserToken();
        post("/api/ai/command", token, java.util.Map.of("command", "Create a task to review the schema"))
                .andExpect(status().isOk());
        post("/api/ai/command", token, java.util.Map.of("command", "Do something impossible"))
                .andExpect(status().isOk());

        get("/api/ai/activity?limit=20", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].intent").value("CREATE_TASK"))
                .andExpect(jsonPath("$[1].status").value("SUCCESS"));

        delete("/api/ai/activity", token).andExpect(status().isNoContent());
        get("/api/ai/activity", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the activity trail is private to the user who created it")
    void activityIsPerUser() throws Exception {
        String first = newUserToken();
        String second = newUserToken();
        post("/api/ai/command", first, java.util.Map.of("command", "Show my stats"))
                .andExpect(status().isOk());

        get("/api/ai/activity?limit=20", second).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("provider status is exposed honestly")
    void providerStatus() throws Exception {
        String token = newUserToken();
        get("/api/ai/status", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("local"))
                .andExpect(jsonPath("$.remote").value(false))
                .andExpect(jsonPath("$.supportedIntents.length()").value(11));

        get("/api/ai/intents", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11));
    }
}
