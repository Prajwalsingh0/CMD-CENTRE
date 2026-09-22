package com.aicommandcenter.analytics;

import com.aicommandcenter.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard, analytics, notifications and the research agent.
 *
 * <p>The recurring theme: an empty workspace must render as genuinely empty, and every figure must
 * trace back to a row the user created.</p>
 */
class AnalyticsAndResearchIT extends IntegrationTestBase {

    @Test
    @DisplayName("a brand new workspace reports zeros and empty panels, not placeholder numbers")
    void emptyWorkspaceIsEmpty() throws Exception {
        String token = newUserToken();

        get("/api/dashboard", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayTasks.length()").value(0))
                .andExpect(jsonPath("$.overdueTasks.length()").value(0))
                .andExpect(jsonPath("$.activeGoals.length()").value(0))
                .andExpect(jsonPath("$.recentDocuments.length()").value(0))
                .andExpect(jsonPath("$.recentAiActivity.length()").value(0))
                .andExpect(jsonPath("$.unreadNotificationCount").value(0))
                .andExpect(jsonPath("$.stats.totalTasks").value(0))
                .andExpect(jsonPath("$.stats.completionRate").value(0));

        get("/api/analytics?days=30", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completionRate").value(0))
                .andExpect(jsonPath("$.tasksCompleted.length()").value(30));

        get("/api/notifications", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        get("/api/notifications/unread-count", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("analytics reflect the tasks and documents that actually exist")
    void analyticsFollowRealData() throws Exception {
        String token = newUserToken();
        createGoal(token, "Land a backend role", LocalDate.now().plusDays(20).toString());
        long done = createTask(token, "Finish the portfolio project", null, null);
        createTask(token, "Overdue thing", LocalDate.now().minusDays(1).toString(), null);

        patch("/api/tasks/" + done + "/status", token, Map.of("status", "COMPLETED")).andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown",
                "# Notes\n\nJava, Spring Boot and PostgreSQL notes for revision.".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents").file(file)
                .header("Authorization", "Bearer " + token)).andExpect(status().isCreated());

        get("/api/analytics?days=30", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.openTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(50))
                .andExpect(jsonPath("$.activeGoals").value(1))
                .andExpect(jsonPath("$.documents").value(1))
                .andExpect(jsonPath("$.indexedChunks").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.goalProgress[0].progressPercent").value(0))
                .andExpect(jsonPath("$.tasksByStatus").isArray())
                .andExpect(jsonPath("$.tasksByPriority").isArray());

        // The most recent series point is today and counts the task completed a moment ago.
        var series = bodyOf(get("/api/analytics?days=7", token)).path("tasksCompleted");
        assertThat(series.get(series.size() - 1).path("date").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(series.get(series.size() - 1).path("count").asLong()).isEqualTo(1);

        get("/api/dashboard", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueTasks.length()").value(1))
                .andExpect(jsonPath("$.recentDocuments.length()").value(1))
                .andExpect(jsonPath("$.upcomingDeadlines.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.stats.completionRate").value(50));
    }

    @Test
    @DisplayName("notifications are derived once and are idempotent")
    void notificationsAreDerivedAndDeduplicated() throws Exception {
        String token = newUserToken();
        createTask(token, "Late task", LocalDate.now().minusDays(4).toString(), null);
        createTask(token, "Due today", LocalDate.now().toString(), null);

        post("/api/notifications/refresh", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.eventType=='OVERDUE_TASK')]").exists())
                .andExpect(jsonPath("$[?(@.eventType=='DEADLINE_SOON')]").exists());

        // Refreshing again must not duplicate anything.
        post("/api/notifications/refresh", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        get("/api/notifications/unread-count", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        long firstId = bodyOf(get("/api/notifications", token)).get(0).path("id").asLong();
        post("/api/notifications/" + firstId + "/read", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        get("/api/notifications/unread-count", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        post("/api/notifications/read-all", token).andExpect(status().isOk());
        get("/api/notifications/unread-count", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        String other = newUserToken();
        delete("/api/notifications/" + firstId, other).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the research agent states its grounding and never fabricates sources")
    void researchIsHonestAboutGrounding() throws Exception {
        String token = newUserToken();

        var withoutSources = bodyOf(post("/api/research", token,
                Map.of("topic", "Kubernetes autoscaling"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.sources.length()").value(0))
                .andExpect(jsonPath("$.disclosure").isNotEmpty()));
        assertThat(withoutSources.path("disclosure").asText())
                .containsIgnoringCase("retrieval");

        MockMultipartFile file = new MockMultipartFile("file", "autoscaling.md", "text/markdown",
                ("# Kubernetes autoscaling\n\n"
                        + "Horizontal Pod Autoscaler scales replicas from observed CPU utilisation. "
                        + "Vertical Pod Autoscaler adjusts requests and limits. Cluster Autoscaler adds nodes "
                        + "when pods cannot be scheduled. Combine all three for elastic capacity.")
                        .getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents").file(file)
                .header("Authorization", "Bearer " + token)).andExpect(status().isCreated());

        bodyOf(post("/api/research", token, Map.of("topic", "Kubernetes autoscaling"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.keyFindings").isArray())
                .andExpect(jsonPath("$.recommendations").isArray()));

        get("/api/research", token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}
