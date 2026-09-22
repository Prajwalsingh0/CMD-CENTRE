package com.aicommandcenter.ai.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalysisTest {

    private static final String DOCUMENT = """
            Spring Boot auto-configures a servlet container and an application context. It reads configuration
            from application properties and environment variables.

            Spring Boot Actuator exposes health and metrics endpoints for production monitoring. Actuator
            endpoints must be secured because they reveal internal state.

            Testing Spring Boot applications is easiest with @SpringBootTest and MockMvc, which start the
            context once and exercise the controller layer.
            """;

    @Test
    @DisplayName("sentences are split, trimmed and filtered for noise")
    void sentenceSplitting() {
        List<String> sentences = TextAnalysis.sentences(DOCUMENT);
        assertThat(sentences).hasSizeGreaterThanOrEqualTo(3);
        assertThat(sentences).allSatisfy(sentence -> assertThat(sentence).doesNotContain("\n"));
        assertThat(TextAnalysis.sentences("")).isEmpty();
        assertThat(TextAnalysis.sentences(null)).isEmpty();
        assertThat(TextAnalysis.sentences("short")).isEmpty();
    }

    @Test
    @DisplayName("stopwords are dropped and technical tokens survive")
    void tokenisation() {
        List<String> tokens = TextAnalysis.tokens("The Spring Boot actuator is exposed via HTTP and the JVM.");
        assertThat(tokens).contains("spring", "boot", "actuator", "http", "jvm");
        assertThat(tokens).doesNotContain("the", "is", "and", "via");
    }

    @Test
    @DisplayName("keyword ranking is deterministic and frequency ordered")
    void keywordRanking() {
        List<String> keywords = TextAnalysis.topKeywords(DOCUMENT, 5);
        assertThat(keywords).hasSize(5);
        assertThat(keywords).contains("spring", "boot");
        assertThat(TextAnalysis.topKeywords(DOCUMENT, 5)).isEqualTo(keywords);
    }

    @Test
    @DisplayName("summarisation keeps reading order and respects the sentence budget")
    void summarisation() {
        List<String> summary = TextAnalysis.summarize(DOCUMENT, 2);
        assertThat(summary).hasSize(2);
        List<String> all = TextAnalysis.sentences(DOCUMENT);
        assertThat(all.indexOf(summary.get(0))).isLessThan(all.indexOf(summary.get(1)));
    }

    @Test
    @DisplayName("key points are de-duplicated by their leading keywords")
    void keyPointsAreDeduplicated() {
        List<String> points = TextAnalysis.keyPoints(DOCUMENT, 4);
        assertThat(points).isNotEmpty();
        assertThat(points.size()).isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("generated questions reference actual keywords from the text")
    void questionsUseRealKeywords() {
        List<String> questions = TextAnalysis.questions(DOCUMENT, 3);
        assertThat(questions).hasSize(3);
        assertThat(questions).allSatisfy(question -> assertThat(question).endsWith("?"));
    }
}
