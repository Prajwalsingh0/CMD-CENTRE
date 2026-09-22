package com.aicommandcenter.job;

import com.aicommandcenter.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Job intelligence. The central guarantee under test: the system only ever claims a skill is
 * known when the user declared it, and it distinguishes declared-but-unverified from missing.
 */
class JobIntelligenceIT extends IntegrationTestBase {

    private static final String JOB_DESCRIPTION = """
            Job Title: Senior Java Backend Engineer
            Company: Northwind Systems

            About the role
            You will design and run services that handle millions of requests per day.

            Key Responsibilities
            - Design and build REST APIs with Spring Boot and Java 21
            - Model data in PostgreSQL and tune slow queries
            - Containerise services with Docker and ship them through CI/CD
            - Take part in on-call rotation and incident reviews

            Requirements
            - 5+ years of professional Java and Spring Boot experience
            - Strong SQL and relational data modelling skills
            - Experience with AWS and Kubernetes in production
            - Bachelor degree in Computer Science or equivalent experience

            Preferred
            - Kafka for event-driven communication
            - Terraform for infrastructure as code
            """;

    private long analyze(String token) throws Exception {
        return bodyOf(post("/api/jobs/analyze", token, Map.of("description", JOB_DESCRIPTION))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    private void declareSkill(String token, String skill, boolean verified) throws Exception {
        post("/api/users/me/skills", token, Map.of("skill", skill, "level", "ADVANCED", "verified", verified))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the description is parsed into a structured analysis")
    void descriptionIsParsed() throws Exception {
        String token = newUserToken();
        var analysis = bodyOf(post("/api/jobs/analyze", token, Map.of("description", JOB_DESCRIPTION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredSkills").isArray())
                .andExpect(jsonPath("$.interviewQuestions").isArray())
                .andExpect(jsonPath("$.learningPlan").isArray()));

        assertThat(analysis.path("jobTitle").asText()).containsIgnoringCase("java");
        assertThat(analysis.path("experience").asText()).contains("5");

        List<String> required = objectMapper.convertValue(analysis.path("requiredSkills"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        assertThat(required).contains("Java", "Spring Boot", "SQL", "Docker");
        assertThat(required).doesNotContain("Terraform");

        List<String> preferred = objectMapper.convertValue(analysis.path("preferredSkills"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        assertThat(preferred).contains("Terraform");
    }

    @Test
    @DisplayName("skills are classified as known, unverified or missing — never invented")
    void skillClassificationIsHonest() throws Exception {
        String token = newUserToken();
        declareSkill(token, "Java", true);
        declareSkill(token, "Spring Boot", true);
        declareSkill(token, "Docker", false);

        var analysis = bodyOf(post("/api/jobs/analyze", token, Map.of("description", JOB_DESCRIPTION))
                .andExpect(status().isCreated()));

        assertThat(statusOf(analysis, "Java")).isEqualTo("KNOWN");
        assertThat(statusOf(analysis, "Spring Boot")).isEqualTo("KNOWN");
        assertThat(statusOf(analysis, "Docker")).isEqualTo("UNVERIFIED");
        assertThat(statusOf(analysis, "SQL")).isEqualTo("MISSING");
        assertThat(statusOf(analysis, "AWS")).isEqualTo("MISSING");

        List<String> missing = objectMapper.convertValue(analysis.path("missingSkills"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        assertThat(missing).contains("SQL", "AWS", "Kubernetes").doesNotContain("Java", "Docker");

        int score = analysis.path("matchScore").asInt();
        assertThat(score).isBetween(1, 99);
        assertThat(analysis.path("gapAnalysis").asText()).contains("never assumes");
    }

    @Test
    @DisplayName("a profile with no skills scores zero rather than assuming competence")
    void emptyProfileScoresNothing() throws Exception {
        String token = newUserToken();
        var analysis = bodyOf(post("/api/jobs/analyze", token, Map.of("description", JOB_DESCRIPTION))
                .andExpect(status().isCreated()));
        assertThat(analysis.path("matchScore").asInt()).isZero();
        assertThat(analysis.path("gapAnalysis").asText()).contains("0%");
    }

    @Test
    @DisplayName("an unknown spelling of a declared skill still matches")
    void skillAliasesMatch() throws Exception {
        String token = newUserToken();
        declareSkill(token, "springboot", true);
        declareSkill(token, "postgres", true);

        var analysis = bodyOf(post("/api/jobs/analyze", token, Map.of("description", JOB_DESCRIPTION))
                .andExpect(status().isCreated()));
        assertThat(statusOf(analysis, "Spring Boot")).isEqualTo("KNOWN");
        assertThat(statusOf(analysis, "PostgreSQL")).isEqualTo("KNOWN");
    }

    @Test
    @DisplayName("interview preparation can be regenerated for a stored analysis")
    void preparationCanBeRegenerated() throws Exception {
        String token = newUserToken();
        long id = analyze(token);

        var regenerated = bodyOf(post("/api/jobs/" + id + "/prep", token).andExpect(status().isOk()));
        assertThat(regenerated.path("interviewQuestions").size()).isGreaterThan(0);
        assertThat(regenerated.path("interviewTopics").size()).isGreaterThan(0);
        assertThat(regenerated.path("learningPlan").size()).isGreaterThan(0);

        get("/api/jobs", token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a job description that is too short is refused")
    void shortDescriptionsAreRefused() throws Exception {
        String token = newUserToken();
        post("/api/jobs/analyze", token, Map.of("description", "Java dev wanted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("analyses are private to the user who created them")
    void analysesArePrivate() throws Exception {
        String owner = newUserToken();
        String attacker = newUserToken();
        long id = analyze(owner);

        get("/api/jobs/" + id, attacker).andExpect(status().isNotFound());
        post("/api/jobs/" + id + "/prep", attacker).andExpect(status().isNotFound());
        delete("/api/jobs/" + id, attacker).andExpect(status().isNotFound());
        get("/api/jobs", attacker).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        delete("/api/jobs/" + id, owner).andExpect(status().isNoContent());
    }

    private String statusOf(com.fasterxml.jackson.databind.JsonNode analysis, String skill) {
        for (com.fasterxml.jackson.databind.JsonNode match : analysis.path("skillMatches")) {
            if (skill.equalsIgnoreCase(match.path("skill").asText())) {
                return match.path("status").asText();
            }
        }
        return "ABSENT";
    }
}
