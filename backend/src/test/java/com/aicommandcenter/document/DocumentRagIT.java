package com.aicommandcenter.document;

import com.aicommandcenter.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Document upload, validation, extraction, insight and retrieval.
 *
 * <p>The rejection cases matter as much as the happy path: a renamed binary, an unsupported
 * extension and a cross-user read must all fail in a controlled way.</p>
 */
class DocumentRagIT extends IntegrationTestBase {

    private static final String NOTES = """
            # Spring Security notes

            Spring Security protects the application with a filter chain. Authentication establishes who the
            caller is, while authorisation decides which resources that caller may touch.

            JSON Web Tokens carry the authenticated subject as a signed claim. The signature is verified on
            every request, and an expired token is rejected. Passwords are hashed with BCrypt and never
            stored or returned in plain text.

            Stateless authentication means the server keeps no session, so CSRF protection can be disabled
            for the API while CORS is restricted to an explicit allow-list.
            """;

    private static final String RESUME = """
            Backend engineer with four years of experience building Java services.

            Core skills: Java, Spring Boot, PostgreSQL, Hibernate, JUnit and Docker.
            Comfortable with Maven, Flyway migrations, REST API design and production debugging.
            """;

    private long upload(String token, String name, String contentType, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, contentType,
                content.getBytes(StandardCharsets.UTF_8));
        return bodyOf(mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    @Test
    @DisplayName("a markdown upload is stored, extracted, chunked and indexed")
    void uploadIsIndexed() throws Exception {
        String token = newUserToken();
        long documentId = upload(token, "spring-security.md", "text/markdown", NOTES);

        get("/api/documents/" + documentId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.extractedChars").value(org.hamcrest.Matchers.greaterThan(200)))
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        get("/api/documents/" + documentId + "/content", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("filter chain")))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    @DisplayName("insights run through the provider and are recorded in the activity trail")
    void insightsAreGenerated() throws Exception {
        String token = newUserToken();
        long documentId = upload(token, "notes.md", "text/markdown", NOTES);

        String summary = bodyOf(post("/api/documents/" + documentId + "/insights", token,
                java.util.Map.of("operation", "SUMMARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("SUMMARY"))
                .andExpect(jsonPath("$.provider").value("local")))
                .path("content").asText();
        assertThat(summary).isNotBlank();

        post("/api/documents/" + documentId + "/insights", token,
                java.util.Map.of("operation", "KEY_POINTS")).andExpect(status().isOk());
        post("/api/documents/" + documentId + "/insights", token,
                java.util.Map.of("operation", "NOTES")).andExpect(status().isOk());
        post("/api/documents/" + documentId + "/insights", token,
                java.util.Map.of("operation", "QUESTIONS")).andExpect(status().isOk());

        get("/api/ai/activity?limit=20", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        post("/api/documents/" + documentId + "/insights", token,
                java.util.Map.of("operation", "NOT_A_REAL_OPERATION"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a question answered by the documents comes back grounded and cited")
    void groundedQuestionIsAnsweredWithSources() throws Exception {
        String token = newUserToken();
        upload(token, "spring-security.md", "text/markdown", NOTES);

        bodyOf(post("/api/knowledge/ask", token,
                java.util.Map.of("question", "How are passwords stored and is authentication stateless?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.provider").value("local"))
                .andExpect(jsonPath("$.sources.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.sources[0].documentName").value("spring-security.md")));
    }

    @Test
    @DisplayName("an empty knowledge base is reported honestly instead of answered")
    void emptyKnowledgeBaseIsHonest() throws Exception {
        String token = newUserToken();
        post("/api/knowledge/ask", token, java.util.Map.of("question", "What did I promise the client?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.sources.length()").value(0))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("empty")));
    }

    @Test
    @DisplayName("a document belonging to someone else cannot be read or asked about")
    void documentsArePrivateToTheirOwner() throws Exception {
        String owner = newUserToken();
        String attacker = newUserToken();
        long documentId = upload(owner, "resume.md", "text/markdown", RESUME);

        get("/api/documents/" + documentId, attacker).andExpect(status().isNotFound());
        get("/api/documents/" + documentId + "/content", attacker).andExpect(status().isNotFound());
        delete("/api/documents/" + documentId, attacker).andExpect(status().isNotFound());
        post("/api/documents/" + documentId + "/insights", attacker,
                java.util.Map.of("operation", "SUMMARY")).andExpect(status().isNotFound());

        // Asking a question scoped to a document the caller does not own is refused, not ignored.
        post("/api/knowledge/ask", attacker,
                java.util.Map.of("question", "What are the skills?", "documentIds", java.util.List.of(documentId)))
                .andExpect(status().isNotFound());

        // The owner still sees the document.
        get("/api/documents/" + documentId, owner).andExpect(status().isOk());
    }

    @Test
    @DisplayName("only allow-listed file types with matching content are accepted")
    void uploadValidation() throws Exception {
        String token = newUserToken();

        MockMultipartFile executable = new MockMultipartFile("file", "payload.exe",
                "application/octet-stream", new byte[]{0x4D, 0x5A, 0x00, 0x01});
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(executable).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_FILE_TYPE"));

        MockMultipartFile disguised = new MockMultipartFile("file", "invoice.pdf",
                "application/pdf", "I am definitely not a PDF".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(disguised).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTENT_TYPE_MISMATCH"));

        MockMultipartFile binaryText = new MockMultipartFile("file", "notes.txt",
                "text/plain", new byte[]{'h', 'i', 0x00, 0x01, 0x02});
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(binaryText).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTENT_TYPE_MISMATCH"));

        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(empty).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a path-traversal file name is neutralised, not honoured")
    void traversalNamesAreSanitised() throws Exception {
        String token = newUserToken();
        MockMultipartFile sneaky = new MockMultipartFile("file", "../../../../etc/passwd.md",
                "text/markdown", "harmless note".getBytes(StandardCharsets.UTF_8));

        long id = bodyOf(mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(sneaky).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()))
                .path("id").asLong();

        String storedName = bodyOf(get("/api/documents/" + id, token)).path("name").asText();
        assertThat(storedName).doesNotContain("..").doesNotContain("/").doesNotContain("\\");
    }

    @Test
    @DisplayName("reindexing re-embeds every stored passage")
    void reindexRebuildsVectors() throws Exception {
        String token = newUserToken();
        upload(token, "resume.md", "text/markdown", RESUME);

        post("/api/knowledge/reindex", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsProcessed").value(1))
                .andExpect(jsonPath("$.chunksEmbedded").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.provider").value("local"));
    }

    @Test
    @DisplayName("deleting a document removes its passages from retrieval")
    void deleteRemovesRetrievalData() throws Exception {
        String token = newUserToken();
        long documentId = upload(token, "resume.md", "text/markdown", RESUME);

        delete("/api/documents/" + documentId, token).andExpect(status().isNoContent());

        post("/api/knowledge/reindex", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.chunksEmbedded").value(0));

        post("/api/knowledge/ask", token, java.util.Map.of("question", "What are the core skills?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false));
    }
}
