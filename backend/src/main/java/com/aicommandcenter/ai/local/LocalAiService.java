package com.aicommandcenter.ai.local;

import com.aicommandcenter.ai.AiCompletion;
import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The default provider: deterministic, offline, credential-free.
 *
 * <p>It exists for three reasons. First, the repository must be runnable by a reviewer with
 * no API key. Second, tests must not depend on a network. Third, it makes the "AI can fail"
 * path in the rest of the application real rather than theoretical, because
 * {@link #supportsStructuredOutput()} is {@code false} and every structured caller therefore
 * exercises its deterministic fallback on the default configuration.</p>
 *
 * <p>It is a genuine implementation, not a stub: summarisation, keyword extraction, note and
 * question generation are real algorithms, and {@link #embed(List)} produces stable
 * normalised vectors that give usable lexical retrieval.</p>
 */
public class LocalAiService implements AiService {

    public static final String NAME = "local";

    private final int dimensions;

    public LocalAiService(int dimensions) {
        this.dimensions = Math.max(32, dimensions);
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    /**
     * Deliberately {@code false}. A deterministic analyser must not pretend it can reason about
     * a JSON contract, so command parsing, job parsing and research planning all use their
     * purpose-built deterministic implementations instead of a prompt.
     */
    @Override
    public boolean supportsStructuredOutput() {
        return false;
    }

    @Override
    public int embeddingDimensions() {
        return dimensions;
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        String body = request.lastUserMessage();
        String text = switch (request.task()) {
            case SUMMARIZE -> summarise(body);
            case KEY_POINTS -> bullets(body);
            case NOTES -> notes(body);
            case QUESTIONS -> numberedQuestions(body, 8);
            case ANSWER -> answer(body);
            case RESEARCH -> researchSkeleton(body);
            case GENERIC -> generic(body);
        };
        return new AiCompletion(text, NAME, "deterministic-analyser", false);
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        List<double[]> vectors = new ArrayList<>();
        if (texts == null) {
            return vectors;
        }
        for (String text : texts) {
            vectors.add(embedOne(text));
        }
        return vectors;
    }

    /** Hashed bag-of-words vector, L2-normalised so cosine similarity is a dot product. */
    public double[] embedOne(String text) {
        double[] vector = new double[dimensions];
        List<String> tokens = TextAnalysis.tokens(text);
        for (String token : tokens) {
            vector[Math.floorMod(token.hashCode(), dimensions)] += 1.0;
        }
        // Bigrams add a little word-order signal, which noticeably helps phrase questions.
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String bigram = tokens.get(i) + " " + tokens.get(i + 1);
            vector[Math.floorMod(bigram.hashCode(), dimensions)] += 0.5;
        }
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / norm;
            }
        }
        return vector;
    }

    private String summarise(String body) {
        List<String> summary = TextAnalysis.summarize(body, 5);
        if (summary.isEmpty()) {
            return "The supplied text is too short to summarise meaningfully.";
        }
        return "Summary of the supplied text:\n" + TextAnalysis.bullets(summary);
    }

    private String bullets(String body) {
        List<String> points = TextAnalysis.keyPoints(body, 7);
        if (points.isEmpty()) {
            return "No key points could be extracted from the supplied text.";
        }
        return "Key points:\n" + TextAnalysis.bullets(points);
    }

    private String notes(String body) {
        List<String> topics = TextAnalysis.topKeywords(body, 8);
        List<String> summary = TextAnalysis.summarize(body, 4);
        StringBuilder builder = new StringBuilder("Notes\n=====\n\n");
        builder.append("Focus areas:\n").append(TextAnalysis.bullets(topics)).append("\n\n");
        builder.append("Details:\n").append(TextAnalysis.bullets(summary)).append("\n\n");
        builder.append("Next actions:\n").append(TextAnalysis.bullets(List.of(
                "Re-read the source document before acting on these notes.",
                "Convert each focus area above into a concrete task.")));
        return builder.toString();
    }

    private String numberedQuestions(String body, int limit) {
        List<String> questions = TextAnalysis.questions(body, limit);
        if (questions.isEmpty()) {
            return "Not enough content to derive questions.";
        }
        StringBuilder builder = new StringBuilder("Questions\n=========\n");
        for (int i = 0; i < questions.size(); i++) {
            builder.append(i + 1).append(". ").append(questions.get(i)).append('\n');
        }
        return builder.toString();
    }

    private String answer(String body) {
        List<String> lines = body.lines().toList();
        int separator = lines.indexOf("QUESTION:");
        String context = separator < 0 ? body : String.join("\n", lines.subList(0, separator));
        String question = separator < 0 ? "" : String.join("\n", lines.subList(separator + 1, lines.size()));
        List<String> keywords = TextAnalysis.topKeywords(question, 8);
        List<String> support = TextAnalysis.keyPoints(context, 4);
        if (support.isEmpty()) {
            return "The retrieved context does not contain enough information to answer \"" + question.trim()
                    + "\". Try rephrasing the question, or upload a document that covers it.";
        }
        return "Answer (extractive, from the supplied context):\n" + TextAnalysis.bullets(support)
                + (keywords.isEmpty() ? "" : "\nMatched on: " + String.join(", ", keywords));
    }

    private String researchSkeleton(String body) {
        List<String> keywords = TextAnalysis.topKeywords(body, 6);
        return "This provider performs no web retrieval, so the report below is derived only from the "
                + "topic text and the local knowledge base.\n\nFocus areas:\n" + TextAnalysis.bullets(keywords);
    }

    private String generic(String body) {
        List<String> keywords = TextAnalysis.topKeywords(body, 6);
        if (keywords.isEmpty()) {
            return "The local provider has no language model; supply a topic or run with AI_PROVIDER=openai "
                    + "for free-form answers.";
        }
        return "Local provider (no language model). Most relevant terms in your message: "
                + String.join(", ", keywords)
                + ".\nSet AI_PROVIDER=openai and AI_API_KEY to enable free-form answers.";
    }
}
