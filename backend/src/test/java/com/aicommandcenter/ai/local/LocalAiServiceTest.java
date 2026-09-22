package com.aicommandcenter.ai.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiServiceTest {

    private final LocalAiService service = new LocalAiService(256);

    @Test
    @DisplayName("the default provider never claims capabilities it does not have")
    void capabilitiesAreHonest() {
        assertThat(service.providerName()).isEqualTo("local");
        assertThat(service.isRemote()).isFalse();
        assertThat(service.supportsStructuredOutput()).isFalse();
        assertThat(service.embeddingDimensions()).isEqualTo(256);
    }

    @Test
    @DisplayName("embeddings are deterministic, normalised and of the configured size")
    void embeddingsAreStableAndNormalised() {
        double[] first = service.embedOne("Spring Boot security with JWT tokens");
        double[] second = service.embedOne("Spring Boot security with JWT tokens");

        assertThat(first).hasSize(256);
        assertThat(first).isEqualTo(second);

        double norm = 0;
        for (double value : first) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("similar texts score higher than unrelated ones")
    void similarityReflectsMeaning() {
        double[] query = service.embedOne("How do I secure a Spring Boot API?");
        double[] related = service.embedOne("Securing a Spring Boot API with JWT and Spring Security");
        double[] unrelated = service.embedOne("Banana bread recipe with walnuts");

        double relatedScore = com.aicommandcenter.rag.VectorMath.cosine(query, related);
        double unrelatedScore = com.aicommandcenter.rag.VectorMath.cosine(query, unrelated);

        assertThat(relatedScore).isGreaterThan(unrelatedScore);
        assertThat(relatedScore).isGreaterThan(0.2);
    }

    @Test
    @DisplayName("an empty text yields an all-zero vector instead of NaN")
    void emptyTextIsSafe() {
        double[] vector = service.embedOne("");
        assertThat(vector).hasSize(256);
        assertThat(vector).containsOnly(0.0);
    }

    @Test
    @DisplayName("embed returns exactly one vector per input, in order")
    void batchEmbeddingIsAligned() {
        List<double[]> vectors = service.embed(List.of("alpha beta", "gamma delta", "epsilon"));
        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)).isEqualTo(service.embedOne("alpha beta"));
        assertThat(service.embed(List.of())).isEmpty();
        assertThat(service.embed(null)).isEmpty();
    }

    @Test
    @DisplayName("summarise and key points produce real, non-placeholder output")
    void textTasksProduceUsefulOutput() {
        String document = "Kafka provides durable log storage for event streaming. "
                + "Consumers track offsets per partition so replay is possible. "
                + "Retention policies decide how long events remain readable.";

        String summary = service.complete(com.aicommandcenter.ai.AiRequest.of(
                com.aicommandcenter.ai.AiTask.SUMMARIZE, "system", document)).text();
        String points = service.complete(com.aicommandcenter.ai.AiRequest.of(
                com.aicommandcenter.ai.AiTask.KEY_POINTS, "system", document)).text();

        assertThat(summary).contains("Summary").containsIgnoringCase("kafka");
        assertThat(points).contains("Key points").contains("- ");
        assertThat(summary).doesNotContain("TODO").doesNotContain("placeholder");
    }

    @Test
    @DisplayName("answering refuses to invent content when the context is empty")
    void answerWithoutContextSaysSo() {
        String answer = service.complete(com.aicommandcenter.ai.AiRequest.of(
                com.aicommandcenter.ai.AiTask.ANSWER, "system", "\nQUESTION:\nWhat is the refund policy?")).text();
        assertThat(answer).containsIgnoringCase("does not contain enough information");
    }
}
