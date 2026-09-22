package com.aicommandcenter.job.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobDescriptionParserTest {

    private static final String DESCRIPTION = """
            Backend Engineer (Java)
            Company: Contoso Retail

            What you'll do
            - Build and maintain REST APIs backed by PostgreSQL
            - Containerise workloads with Docker

            Requirements
            - 3+ years of experience with Java and Spring Boot
            - Solid understanding of SQL and data structures
            - Bachelor degree in Computer Science

            Nice to have
            - Experience with Kafka
            - Familiarity with Terraform
            """;

    @Test
    @DisplayName("title, company, experience and education are extracted")
    void structuredFieldsAreExtracted() {
        var parsed = JobDescriptionParser.parse(DESCRIPTION);
        assertThat(parsed.jobTitle()).containsIgnoringCase("backend engineer");
        assertThat(parsed.company()).isEqualTo("Contoso Retail");
        assertThat(parsed.experience()).contains("3");
        assertThat(parsed.education()).containsIgnoringCase("bachelor");
    }

    @Test
    @DisplayName("required skills come from requirements and responsibilities; preferred stay separate")
    void skillSectionsAreRespected() {
        var parsed = JobDescriptionParser.parse(DESCRIPTION);
        assertThat(parsed.requiredSkills()).contains("Java", "Spring Boot", "SQL", "PostgreSQL", "Docker");
        assertThat(parsed.requiredSkills()).doesNotContain("Kafka", "Terraform");
        assertThat(parsed.preferredSkills()).contains("Kafka", "Terraform");
        assertThat(parsed.preferredSkills()).doesNotContain("Java");
        assertThat(parsed.technologies()).contains("Java", "Kafka");
    }

    @Test
    @DisplayName("responsibilities are captured as readable items")
    void responsibilitiesAreCaptured() {
        var parsed = JobDescriptionParser.parse(DESCRIPTION);
        assertThat(parsed.responsibilities()).isNotEmpty();
        assertThat(parsed.responsibilities().get(0)).doesNotStartWith("-");
    }

    @Test
    @DisplayName("a description with no headings still yields skills")
    void headinglessDescriptionFallsBackToWholeText() {
        var parsed = JobDescriptionParser.parse(
                "We are hiring a Python developer with strong PostgreSQL, Docker and AWS experience "
                        + "to build data pipelines.");
        assertThat(parsed.requiredSkills()).contains("Python", "PostgreSQL", "Docker", "AWS");
        assertThat(parsed.jobTitle()).containsIgnoringCase("python");
    }

    @Test
    @DisplayName("empty and nonsense input degrade gracefully")
    void degenerateInputs() {
        var empty = JobDescriptionParser.parse("");
        assertThat(empty.requiredSkills()).isEmpty();
        assertThat(empty.technologies()).isEmpty();
        assertThat(empty.responsibilities()).isEmpty();

        var nulls = JobDescriptionParser.parse(null);
        assertThat(nulls.requiredSkills()).isEmpty();
    }

    @Test
    @DisplayName("skill matching respects word boundaries and aliases")
    void dictionaryMatching() {
        assertThat(SkillDictionary.findSkills("We use Java and JavaScript daily"))
                .containsExactlyInAnyOrder("Java", "JavaScript");
        assertThat(SkillDictionary.findSkills("Experience with Node.js and React"))
                .contains("Node.js", "React");
        assertThat(SkillDictionary.findSkills("Strong C++ and C# background"))
                .contains("C++", "C#");
        assertThat(SkillDictionary.findSkills("Kubernetes, k8s or OpenShift experience"))
                .contains("Kubernetes");
    }

    @Test
    @DisplayName("user-entered spellings canonicalise to the dictionary label")
    void canonicalisation() {
        assertThat(SkillDictionary.canonicalise("springboot")).isEqualTo("Spring Boot");
        assertThat(SkillDictionary.canonicalise("  POSTGRES ")).isEqualTo("PostgreSQL");
        assertThat(SkillDictionary.canonicalise("JAVA")).isEqualTo("Java");
        assertThat(SkillDictionary.canonicalise("Kubernetes")).isEqualTo("Kubernetes");
        assertThat(SkillDictionary.canonicalise("Woodworking")).isEqualTo("Woodworking");
        assertThat(SkillDictionary.canonicalise(null)).isEmpty();
    }

    @Test
    @DisplayName("the dictionary itself is free of duplicates and blanks")
    void dictionaryIsConsistent() {
        List<String> skills = SkillDictionary.allSkills();
        assertThat(skills).doesNotHaveDuplicates();
        assertThat(skills).allSatisfy(skill -> assertThat(skill).isNotBlank());
        assertThat(skills).contains("Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes");
    }
}
