package com.aicommandcenter.task;

import com.aicommandcenter.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recurring tasks over HTTP: completing an occurrence queues the next one, exactly once.
 */
class TaskRecurrenceIT extends IntegrationTestBase {

    @Test
    @DisplayName("completing a weekly task queues the next occurrence with the date advanced")
    void completingAWeeklyTaskQueuesTheNext() throws Exception {
        String token = newUserToken();
        LocalDate due = LocalDate.of(2026, 3, 2);

        JsonNode created = createTask(token, "Weekly review", due, "WEEKLY");
        assertThat(created.path("recurrence").asText()).isEqualTo("WEEKLY");
        long firstId = created.path("id").asLong();

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/tasks/" + firstId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        JsonNode successor = findTask(token, "Weekly review", "TODO");
        assertThat(successor).isNotNull();
        assertThat(successor.path("dueDate").asText()).isEqualTo(due.plusWeeks(1).toString());
        assertThat(successor.path("recurrence").asText()).isEqualTo("WEEKLY");
        assertThat(successor.path("id").asLong()).isNotEqualTo(firstId);
    }

    @Test
    @DisplayName("a monthly task due at a month end recurs on the clamped date")
    void monthlyTaskClampsTheDate() throws Exception {
        String token = newUserToken();
        JsonNode created = createTask(token, "Close the books", LocalDate.of(2026, 1, 31), "MONTHLY");

        completeTask(token, created.path("id").asLong());

        JsonNode successor = findTask(token, "Close the books", "TODO");
        assertThat(successor).isNotNull();
        assertThat(successor.path("dueDate").asText()).isEqualTo("2026-02-28");
    }

    @Test
    @DisplayName("completing an occurrence twice does not fan out")
    void completingTwiceQueuesOnlyOne() throws Exception {
        String token = newUserToken();
        long id = createTask(token, "Daily standup note", LocalDate.of(2026, 3, 2), "DAILY").path("id").asLong();

        completeTask(token, id);
        // A second completion of the same task is a no-op, not another occurrence
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/tasks/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        assertThat(countTasks(token, "Daily standup note", "TODO")).isEqualTo(1);
    }

    @Test
    @DisplayName("a one-off task queues nothing when completed")
    void oneOffTasksQueueNothing() throws Exception {
        String token = newUserToken();
        long id = createTask(token, "Send the invoice", LocalDate.of(2026, 3, 2), "NONE").path("id").asLong();

        completeTask(token, id);

        assertThat(countTasks(token, "Send the invoice", "TODO")).isZero();
        assertThat(countTasks(token, "Send the invoice", "COMPLETED")).isEqualTo(1);
    }

    @Test
    @DisplayName("a series cannot be created without a date to advance from")
    void recurrenceWithoutDueDateIsRejected() throws Exception {
        String token = newUserToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "No date, no series");
        body.put("recurrence", "WEEKLY");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a one-off task can still be created without a due date")
    void oneOffTaskNeedsNoDate() throws Exception {
        String token = newUserToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Someday task");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrence").value("NONE"));
    }

    @Test
    @DisplayName("completing through the update endpoint queues the next occurrence too")
    void completionThroughUpdateQueuesAsWell() throws Exception {
        String token = newUserToken();
        long id = createTask(token, "Update-path task", LocalDate.of(2026, 5, 4), "WEEKLY").path("id").asLong();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "COMPLETED");
        mockMvc.perform(MockMvcRequestBuilders.put("/api/tasks/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        JsonNode successor = findTask(token, "Update-path task", "TODO");
        assertThat(successor).isNotNull();
        assertThat(successor.path("dueDate").asText()).isEqualTo("2026-05-11");
    }

    @Test
    @DisplayName("one account's recurring task never queues a task for another account")
    void successorsStayWithTheirOwner() throws Exception {
        String mine = newUserToken();
        String theirs = newUserToken();

        long id = createTask(mine, "My weekly thing", LocalDate.of(2026, 3, 2), "WEEKLY").path("id").asLong();
        completeTask(mine, id);

        assertThat(countTasks(mine, "My weekly thing", "TODO")).isEqualTo(1);
        assertThat(countTasks(theirs, "My weekly thing", "TODO")).isZero();
    }

    private JsonNode createTask(String token, String title, LocalDate dueDate, String recurrence) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("dueDate", dueDate.toString());
        body.put("recurrence", recurrence);

        String json = mockMvc.perform(MockMvcRequestBuilders.post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private void completeTask(String token, long id) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/tasks/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
    }

    /** The most recently created task with this title and status, or null. */
    private JsonNode findTask(String token, String title, String status) throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/tasks?size=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(json).path("items");
        for (JsonNode item : items) {
            if (title.equals(item.path("title").asText()) && status.equals(item.path("status").asText())) {
                return item;
            }
        }
        return null;
    }

    private long countTasks(String token, String title, String status) throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/tasks?size=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long count = 0;
        for (JsonNode item : objectMapper.readTree(json).path("items")) {
            if (title.equals(item.path("title").asText()) && status.equals(item.path("status").asText())) {
                count++;
            }
        }
        return count;
    }
}
