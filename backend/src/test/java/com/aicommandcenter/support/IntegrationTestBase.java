package com.aicommandcenter.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared harness for the HTTP-level tests.
 *
 * <p>Every test creates its own user with a random email. The H2 database and the Spring context
 * are shared across classes for speed, so tests must never depend on global counts — the helpers
 * here exist to make that easy to respect.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Sup3rSecret!pass";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /** Registers a fresh user and returns a usable bearer token. */
    protected String newUserToken() throws Exception {
        return register(uniqueEmail("user"));
    }

    protected String register(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("displayName", "Test User");
        String json = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("accessToken").asText();
    }

    protected ResultActions get(String url, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token));
    }

    protected ResultActions post(String url, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token));
    }

    protected ResultActions post(String url, String token, Object body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions put(String url, String token, Object body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions patch(String url, String token, Object body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.patch(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete(url).header("Authorization", "Bearer " + token));
    }

    protected JsonNode bodyOf(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected long createTask(String token, String title) throws Exception {
        return createTask(token, title, null, null);
    }

    protected long createTask(String token, String title, String dueDate, Long goalId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        if (dueDate != null) {
            body.put("dueDate", dueDate);
        }
        if (goalId != null) {
            body.put("goalId", goalId);
        }
        JsonNode response = bodyOf(post("/api/tasks", token, body).andExpect(status().isCreated()));
        return response.path("id").asLong();
    }

    protected long createGoal(String token, String title, String deadline) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        if (deadline != null) {
            body.put("deadline", deadline);
        }
        JsonNode response = bodyOf(post("/api/goals", token, body).andExpect(status().isCreated()));
        return response.path("id").asLong();
    }
}
