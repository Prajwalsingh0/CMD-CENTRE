package com.aicommandcenter.goal;

import com.aicommandcenter.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goal hierarchies: what a tree may look like, and the four ways it must not be corrupted.
 */
class GoalHierarchyIT extends IntegrationTestBase {

    @Test
    @DisplayName("a sub-goal records its parent and the parent counts it")
    void subGoalLinksBothWays() throws Exception {
        String token = newUserToken();
        long parentId = createNestedGoal(token, "Parent goal", null);
        long childId = createNestedGoal(token, "Child goal", parentId);

        JsonNode child = getGoal(token, childId);
        assertThat(child.path("parentGoalId").asLong()).isEqualTo(parentId);
        assertThat(child.path("subGoalCount").asInt()).isZero();

        JsonNode parent = getGoal(token, parentId);
        assertThat(parent.path("parentGoalId").isNull()).isTrue();
        assertThat(parent.path("subGoalCount").asInt()).isEqualTo(1);

        JsonNode listed = listGoals(token);
        assertThat(listed).extracting(node -> node.path("title").asText())
                .contains("Parent goal", "Child goal");
    }

    @Test
    @DisplayName("three levels are allowed, a fourth is refused")
    void depthIsCappedAtThree() throws Exception {
        String token = newUserToken();
        long level1 = createNestedGoal(token, "Level one", null);
        long level2 = createNestedGoal(token, "Level two", level1);
        long level3 = createNestedGoal(token, "Level three", level2);

        assertThat(getGoal(token, level3).path("parentGoalId").asLong()).isEqualTo(level2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Level four");
        body.put("parentGoalId", level3);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("moving a subtree into its own descendant is refused as a cycle")
    void cyclesAreRefused() throws Exception {
        String token = newUserToken();
        long grandparent = createNestedGoal(token, "Grandparent", null);
        long parent = createNestedGoal(token, "Middle", grandparent);

        // Making the grandparent a child of its own child would close a loop
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentGoalId", parent);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/goals/" + grandparent)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a goal cannot be its own parent")
    void selfParentIsRefused() throws Exception {
        String token = newUserToken();
        long goalId = createNestedGoal(token, "Self-parent attempt", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentGoalId", goalId);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a goal cannot be parented to another account's goal")
    void otherAccountsCannotBeParents() throws Exception {
        String mine = newUserToken();
        String theirs = newUserToken();
        long theirGoal = createNestedGoal(theirs, "Not yours", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Sneaky child");
        body.put("parentGoalId", theirGoal);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/goals")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("moved subtrees carry their height into the cap check")
    void subtreeHeightCountsTowardsTheCap() throws Exception {
        String token = newUserToken();
        long root = createNestedGoal(token, "Root", null);
        long secondLevel = createNestedGoal(token, "Second level", root);

        long subtreeTop = createNestedGoal(token, "Subtree top", null);
        createNestedGoal(token, "Subtree child", subtreeTop);

        // secondLevel already sits at level two, and the subtree brings one level with it: the
        // result would be four levels, so the move has to be refused
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentGoalId", secondLevel);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/goals/" + subtreeTop)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // The same move with a childless goal lands exactly on the cap, so it is allowed
        long leaf = createNestedGoal(token, "Flatten me", null);
        Map<String, Object> allowed = new LinkedHashMap<>();
        allowed.put("parentGoalId", secondLevel);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/goals/" + leaf)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowed)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a goal with sub-goals cannot be deleted until they are dealt with")
    void deletingAParentIsRefusedWhileChildrenExist() throws Exception {
        String token = newUserToken();
        long parentId = createNestedGoal(token, "Has children", null);
        long childId = createNestedGoal(token, "The child", parentId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/goals/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/goals/" + childId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/goals/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private long createNestedGoal(String token, String title, Long parentGoalId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        if (parentGoalId != null) {
            body.put("parentGoalId", parentGoalId);
        }
        String json = mockMvc.perform(MockMvcRequestBuilders.post("/api/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("id").asLong();
    }

    private JsonNode getGoal(String token, long id) throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/goals/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode listGoals(String token) throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/goals")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }
}
