package com.aicommandcenter.task;

import com.aicommandcenter.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goals and tasks: CRUD, relationships, filtering, progress, and the ownership checks that stop
 * one account from touching another account's rows.
 */
class TaskGoalIT extends IntegrationTestBase {

    @Test
    @DisplayName("a task can be created, read, updated and deleted")
    void taskCrud() throws Exception {
        String token = newUserToken();
        long taskId = createTask(token, "Prepare Spring Boot revision", LocalDate.now().plusDays(2).toString(), null);

        get("/api/tasks/" + taskId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prepare Spring Boot revision"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.overdue").value(false));

        put("/api/tasks/" + taskId, token, Map.of("title", "Prepare Spring Boot revision (deep)", "priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));

        patch("/api/tasks/" + taskId + "/status", token, Map.of("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        patch("/api/tasks/" + taskId + "/status", token, Map.of("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        delete("/api/tasks/" + taskId, token).andExpect(status().isNoContent());
        get("/api/tasks/" + taskId, token).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("task titles are mandatory and blank titles are refused")
    void taskValidation() throws Exception {
        String token = newUserToken();
        post("/api/tasks", token, Map.of("priority", "HIGH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        post("/api/tasks", token, Map.of("title", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("filtering, searching and sorting run against the caller's own data")
    void taskListFilters() throws Exception {
        String token = newUserToken();
        long goalId = createGoal(token, "Get Java Backend Job", null);
        createTask(token, "Write cover letter", null, goalId);
        createTask(token, "Revise SQL joins", LocalDate.now().minusDays(3).toString(), goalId);
        createTask(token, "Read Spring Security docs", LocalDate.now().plusDays(5).toString(), null);

        long id = bodyOf(get("/api/tasks?search=cover&size=5", token).andExpect(status().isOk()))
                .path("items").get(0).path("id").asLong();
        assertThat(id).isPositive();

        get("/api/tasks?goalId=" + goalId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));

        get("/api/tasks?overdueOnly=true", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Revise SQL joins"))
                .andExpect(jsonPath("$.items[0].overdue").value(true));

        get("/api/tasks/overdue", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        get("/api/tasks?sort=dueDate,desc&size=5", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        get("/api/tasks?sort=notAColumn,desc", token).andExpect(status().isBadRequest());

        get("/api/tasks?size=100000", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("goal progress is derived from its tasks")
    void goalProgressFollowsTasks() throws Exception {
        String token = newUserToken();
        long goalId = createGoal(token, "Ship the portfolio project", LocalDate.now().plusDays(30).toString());
        long first = createTask(token, "Design the schema", null, goalId);
        long second = createTask(token, "Implement APIs", null, goalId);
        long third = createTask(token, "Write tests", null, goalId);

        get("/api/goals/" + goalId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0));

        patch("/api/tasks/" + first + "/status", token, Map.of("status", "COMPLETED")).andExpect(status().isOk());
        get("/api/goals/" + goalId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(33));

        // A cancelled task leaves the denominator, so the goal can still reach 100%.
        patch("/api/tasks/" + second + "/status", token, Map.of("status", "CANCELLED")).andExpect(status().isOk());
        get("/api/goals/" + goalId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(50));

        patch("/api/tasks/" + third + "/status", token, Map.of("status", "COMPLETED")).andExpect(status().isOk());
        get("/api/goals/" + goalId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(100));

        post("/api/goals/" + goalId + "/refresh-status", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("deleting a goal keeps its tasks and detaches them")
    void deletingAGoalDoesNotDestroyTasks() throws Exception {
        String token = newUserToken();
        long goalId = createGoal(token, "Temporary goal", null);
        long taskId = createTask(token, "Task that must survive", null, goalId);

        delete("/api/goals/" + goalId, token).andExpect(status().isNoContent());

        get("/api/tasks/" + taskId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Task that must survive"))
                .andExpect(jsonPath("$.goalId").doesNotExist());
    }

    @Test
    @DisplayName("another user's task, goal or attachment is invisible (IDOR)")
    void crossUserAccessIsNotFound() throws Exception {
        String owner = newUserToken();
        String attacker = newUserToken();

        long goalId = createGoal(owner, "Private goal", null);
        long taskId = createTask(owner, "Private task", null, goalId);

        get("/api/tasks/" + taskId, attacker).andExpect(status().isNotFound());
        put("/api/tasks/" + taskId, attacker, Map.of("title", "Hijacked")).andExpect(status().isNotFound());
        patch("/api/tasks/" + taskId + "/status", attacker, Map.of("status", "CANCELLED"))
                .andExpect(status().isNotFound());
        delete("/api/tasks/" + taskId, attacker).andExpect(status().isNotFound());

        get("/api/goals/" + goalId, attacker).andExpect(status().isNotFound());
        put("/api/goals/" + goalId, attacker, Map.of("title", "Hijacked")).andExpect(status().isNotFound());
        delete("/api/goals/" + goalId, attacker).andExpect(status().isNotFound());

        // Attaching a task to somebody else's goal is refused, not silently ignored.
        post("/api/tasks", attacker, Map.of("title", "Sneaky", "goalId", goalId))
                .andExpect(status().isNotFound());

        // Owner's data is untouched.
        get("/api/tasks/" + taskId, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Private task"));
    }

    @Test
    @DisplayName("list endpoints never mix two users' rows")
    void listsAreIsolated() throws Exception {
        String first = newUserToken();
        String second = newUserToken();
        createTask(first, "Only mine");
        createTask(second, "Only theirs");

        List<String> titles = bodyOf(get("/api/tasks?size=50", first))
                .path("items").findValuesAsText("title");
        assertThat(titles).contains("Only mine").doesNotContain("Only theirs");
    }

    @Test
    @DisplayName("tags round-trip and are searchable")
    void tagsRoundTrip() throws Exception {
        String token = newUserToken();
        long taskId = bodyOf(post("/api/tasks", token,
                Map.of("title", "Practise arrays", "tags", List.of("dsa", "practice")))
                .andExpect(status().isCreated())).path("id").asLong();

        get("/api/tasks/" + taskId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2));

        get("/api/tasks?search=dsa", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
