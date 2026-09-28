package com.aicommandcenter.user;

import com.aicommandcenter.exception.InvalidTokenException;
import com.aicommandcenter.support.IntegrationTestBase;
import com.aicommandcenter.user.entity.RefreshToken;
import com.aicommandcenter.user.entity.User;
import com.aicommandcenter.user.repository.RefreshTokenRepository;
import com.aicommandcenter.user.repository.UserRepository;
import com.aicommandcenter.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session renewal end to end: what a login hands out, what a refresh rotates, and what happens
 * when a token is replayed or raced.
 */
class AuthTokenRefreshIT extends IntegrationTestBase {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("register and login both hand out an access token and a refresh token")
    void bothEntryPointsIssueRefreshTokens() throws Exception {
        String email = uniqueEmail("refresh-issue");
        JsonNode registered = registerWithTokens(email);
        assertThat(registered.path("refreshToken").asText()).isNotBlank();
        assertThat(registered.path("refreshExpiresInSeconds").asLong()).isEqualTo(14 * 86400);
        assertThat(registered.path("expiresInSeconds").asLong()).isEqualTo(15 * 60);
        assertThat(registered.path("refreshToken").asText())
                .isNotEqualTo(registered.path("accessToken").asText());

        Map<String, Object> loginBody = Map.of("email", email, "password", PASSWORD);
        String loginJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(loginJson).path("refreshToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("refreshing returns a new access token and rotates the refresh token")
    void refreshRotatesTheToken() throws Exception {
        JsonNode first = registerWithTokens(uniqueEmail("refresh-rotate"));
        String originalRefresh = first.path("refreshToken").asText();

        JsonNode rotated = refresh(originalRefresh);

        assertThat(rotated.path("accessToken").asText()).isNotBlank();
        assertThat(rotated.path("refreshToken").asText()).isNotBlank().isNotEqualTo(originalRefresh);

        // The new access token is usable on a protected endpoint
        get("/api/auth/me", rotated.path("accessToken").asText())
                .andExpect(status().isOk());

        // And the rotated refresh token can itself be rotated again
        JsonNode second = refresh(rotated.path("refreshToken").asText());
        assertThat(second.path("refreshToken").asText()).isNotEqualTo(rotated.path("refreshToken").asText());
    }

    @Test
    @DisplayName("replaying a rotated token right away is refused without breaking the live session")
    void replayInsideGraceWindowKeepsSession() throws Exception {
        JsonNode first = registerWithTokens(uniqueEmail("refresh-grace"));
        String original = first.path("refreshToken").asText();
        String latest = refresh(original).path("refreshToken").asText();

        mockMvc.perform(refreshRequest(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));

        // The browser-retry interpretation must not punish the legitimate holder
        JsonNode stillWorks = refresh(latest);
        assertThat(stillWorks.path("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("replaying a rotated token later revokes the whole family")
    void replayAfterGraceWindowRevokesFamily() throws Exception {
        String email = uniqueEmail("refresh-reuse");
        JsonNode first = registerWithTokens(email);
        String original = first.path("refreshToken").asText();
        String latest = refresh(original).path("refreshToken").asText();

        // Age the retired token beyond the grace window: an innocent retry is no longer plausible
        Long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        List<RefreshToken> rows = refreshTokenRepository.findAllByUserIdOrderByIdAsc(userId);
        RefreshToken retired = rows.stream().filter(RefreshToken::isRevoked).findFirst().orElseThrow();
        retired.setRevokedAt(Instant.now().minusSeconds(600));
        refreshTokenRepository.save(retired);

        List<RefreshToken> afterAging = refreshTokenRepository.findAllByUserIdOrderByIdAsc(userId);
        RefreshToken agedRow = afterAging.stream().filter(RefreshToken::isRevoked).findFirst().orElseThrow();
        assertThat(agedRow.getRevokedAt())
                .as("the aged row must be persisted before the replay (rows=%s)", afterAging.size())
                .isBefore(Instant.now().minusSeconds(500));

        String replayBody = mockMvc.perform(refreshRequest(original))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(replayBody).contains("INVALID_REFRESH_TOKEN");

        // The family is dead: the newest token no longer renews either
        String latestReplay = mockMvc.perform(refreshRequest(latest))
                .andReturn().getResponse().getContentAsString();
        assertThat(latestReplay)
                .as("the successor must be dead after family revocation; got: %s", latestReplay)
                .contains("INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("two simultaneous refreshes with the same token: exactly one wins")
    void concurrentRefreshAllowsExactlyOneWinner() throws Exception {
        JsonNode first = registerWithTokens(uniqueEmail("refresh-race"));
        String token = first.path("refreshToken").asText();

        int threads = 2;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Callable<Boolean> attempt = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    userService.refresh(token);
                    return true;
                } catch (InvalidTokenException expected) {
                    return false;
                }
            };
            List<Future<Boolean>> futures = pool.invokeAll(List.of(attempt, attempt), 15, TimeUnit.SECONDS);
            List<Boolean> outcomes = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).collect(Collectors.toList());

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logoutRevokesToken() throws Exception {
        JsonNode tokens = registerWithTokens(uniqueEmail("refresh-logout"));
        String refreshToken = tokens.path("refreshToken").asText();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refreshToken", refreshToken);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        mockMvc.perform(refreshRequest(refreshToken)).andExpect(status().isUnauthorized());

        // Logging out again is not an error
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("unknown, malformed and missing refresh tokens are all refused the same way")
    void badRefreshTokensAreRefused() throws Exception {
        String unknown = "z".repeat(64);
        mockMvc.perform(refreshRequest(unknown))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(refreshRequest("short"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("refreshToken", "");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a disabled account cannot renew a session")
    void disabledAccountCannotRefresh() throws Exception {
        String email = uniqueEmail("refresh-disabled");
        JsonNode tokens = registerWithTokens(email);
        String refreshToken = tokens.path("refreshToken").asText();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(refreshRequest(refreshToken)).andExpect(status().isUnauthorized());
    }

    private JsonNode registerWithTokens(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("displayName", "Refresh Tester");
        String json = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String json = mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refreshToken", refreshToken);
        try {
            return MockMvcRequestBuilders.post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
