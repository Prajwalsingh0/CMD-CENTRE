package com.aicommandcenter.user;

import com.aicommandcenter.support.IntegrationTestBase;
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
 * Authentication, token and account-security behaviour.
 *
 * <p>Covers the cases the brief calls out explicitly: valid login, invalid login, unauthorised
 * access, invalid tokens and password safety.</p>
 */
class AuthControllerIT extends IntegrationTestBase {

    @Test
    @DisplayName("register returns a bearer token and never echoes the password hash")
    void registerIssuesTokenWithoutSecrets() throws Exception {
        String email = uniqueEmail("register");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("displayName", "Ada Lovelace");

        String payload = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(payload).doesNotContain("passwordHash");
        assertThat(payload).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("registering the same email twice is a conflict")
    void duplicateRegistrationIsRejected() throws Exception {
        String email = uniqueEmail("dupe");
        register(email);

        Map<String, Object> body = Map.of("email", email, "password", PASSWORD, "displayName", "Copy");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("weak passwords are rejected by validation")
    void weakPasswordIsRejected() throws Exception {
        Map<String, Object> body = Map.of("email", uniqueEmail("weak"), "password", "shortpwd", "displayName", "Weak");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("login succeeds with the right password and fails with the wrong one")
    void loginPaths() throws Exception {
        String email = uniqueEmail("login");
        register(email);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("login does not reveal whether an account exists")
    void unknownAccountGivesTheSameAnswerAsAWrongPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", uniqueEmail("ghost"), "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("protected endpoints require a valid token")
    void protectedRoutesNeedAValidToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/tasks").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());

        String token = newUserToken();
        get("/api/auth/me", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isNotEmpty());
    }

    @Test
    @DisplayName("the health endpoint is public and leaks no configuration")
    void healthIsPublic() throws Exception {
        String payload = mockMvc.perform(MockMvcRequestBuilders.get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.aiProvider").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(payload).doesNotContain("secret").doesNotContain("password");
    }

    @Test
    @DisplayName("profile updates and skills are scoped to the caller")
    void profileAndSkills() throws Exception {
        String token = newUserToken();

        put("/api/users/me", token, Map.of("displayName", "Grace Hopper", "headline", "Backend engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Grace Hopper"))
                .andExpect(jsonPath("$.headline").value("Backend engineer"));

        long skillId = bodyOf(post("/api/users/me/skills", token,
                Map.of("skill", "Java", "level", "ADVANCED", "verified", true))
                .andExpect(status().isCreated())).path("id").asLong();

        get("/api/users/me/skills", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skill").value("Java"));

        delete("/api/users/me/skills/" + skillId, token).andExpect(status().isNoContent());
        get("/api/users/me/skills", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String otherToken = newUserToken();
        delete("/api/users/me/skills/" + skillId, otherToken).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void forgedTokenIsRejected() throws Exception {
        // Structurally valid JWT, signed with an attacker key.
        String forged = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJpc3MiOiJhaS1jb21tYW5kLWNlbnRlciIsInN1YiI6IjEiLCJlbWFpbCI6ImF0dGFja2VyQGV4YW1wbGUuY29tIn0."
                + "ZmFrZXNpZ25hdHVyZWZha2VzaWduYXR1cmVmYWtlc2lnbmF0dXJl";
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }
}
