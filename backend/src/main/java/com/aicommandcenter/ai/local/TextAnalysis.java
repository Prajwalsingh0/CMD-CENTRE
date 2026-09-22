package com.aicommandcenter.ai.local;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic text analytics: sentence splitting, keyword extraction and extractive
 * summarisation. No external service, no randomness — the same input always produces the
 * same output, which is exactly what makes the {@code local} AI provider testable.
 */
public final class TextAnalysis {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?;:])\\s+");
    private static final Pattern WORD = Pattern.compile("[a-z0-9][a-z0-9+#.\\-]{1,}");
    private static final int MIN_SENTENCE_CHARS = 24;
    private static final int MAX_SENTENCE_CHARS = 600;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "your", "with", "this", "that", "these", "those",
            "from", "have", "has", "had", "was", "were", "will", "would", "should", "could", "can", "may",
            "into", "over", "under", "about", "after", "before", "between", "while", "when", "where", "which",
            "who", "whom", "what", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other",
            "some", "such", "only", "own", "same", "than", "too", "very", "just", "also", "its", "it's", "them",
            "they", "their", "there", "here", "then", "them", "out", "off", "our", "ours", "his", "her", "she",
            "him", "he", "been", "being", "does", "did", "doing", "done", "use", "used", "using", "one", "two",
            "get", "got", "make", "made", "like", "well", "much", "many", "you'll", "we'll", "etc", "via");

    private TextAnalysis() {
    }

    /** Splits prose into sentences, dropping fragments that carry no information. */
    public static List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String block : normalised.split("\n+")) {
            if (block.isBlank()) {
                continue;
            }
            for (String candidate : SENTENCE_BOUNDARY.split(block)) {
                String trimmed = candidate.trim().replaceAll("\\s+", " ");
                if (trimmed.length() >= MIN_SENTENCE_CHARS) {
                    result.add(trimmed.length() > MAX_SENTENCE_CHARS
                            ? trimmed.substring(0, MAX_SENTENCE_CHARS) + "…"
                            : trimmed);
                }
            }
        }
        return result;
    }

    /** Lower-cased content words. Keeps compound technology names such as {@code c++}. */
    public static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    /** Token frequencies, most frequent first; ties broken alphabetically for determinism. */
    public static Map<String, Integer> keywordFrequencies(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String token : tokens(text)) {
            counts.merge(token, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed()
                .thenComparing(Map.Entry::getKey));
        Map<String, Integer> ordered = new LinkedHashMap<>();
        entries.forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    public static List<String> topKeywords(String text, int limit) {
        List<String> keys = new ArrayList<>(keywordFrequencies(text).keySet());
        return keys.size() > limit ? keys.subList(0, limit) : keys;
    }

    /**
     * Extractive summary: score every sentence by the summed frequency of its content words,
     * damped by length so a single long sentence cannot dominate, then restore reading order.
     */
    public static List<String> summarize(String text, int maxSentences) {
        List<String> sentences = sentences(text);
        if (sentences.size() <= maxSentences) {
            return sentences;
        }
        Map<String, Integer> frequencies = keywordFrequencies(text);
        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            List<String> tokens = tokens(sentence);
            double score = tokens.stream().mapToInt(t -> frequencies.getOrDefault(t, 0)).sum();
            score = score / Math.sqrt(Math.max(6, tokens.size()));
            // Very early sentences carry disproportionate meaning in resumes, specs and READMEs.
            score += Math.max(0, 3 - i) * 0.35;
            scores.put(i + "|" + sentence, score);
        }
        List<String> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxSentences)
                .map(Map.Entry::getKey)
                .toList();
        List<Integer> indices = ranked.stream()
                .map(key -> Integer.parseInt(key.substring(0, key.indexOf('|'))))
                .sorted()
                .toList();
        List<String> summary = new ArrayList<>();
        for (Integer index : indices) {
            String key = ranked.stream().filter(k -> k.startsWith(index + "|")).findFirst().orElse(null);
            if (key != null) {
                summary.add(key.substring(key.indexOf('|') + 1));
            }
        }
        return summary;
    }

    /** Salient points, de-duplicated by their leading keywords. */
    public static List<String> keyPoints(String text, int limit) {
        List<String> sentences = summarize(text, Math.max(limit * 3, limit));
        Set<String> signatures = new LinkedHashSet<>();
        List<String> points = new ArrayList<>();
        for (String sentence : sentences) {
            String signature = String.join(" ", topKeywords(sentence, 3));
            if (signature.isBlank() || signatures.add(signature)) {
                points.add(sentence);
            }
            if (points.size() >= limit) {
                break;
            }
        }
        return points;
    }

    /** Question stems derived from the dominant keywords of a text. */
    public static List<String> questions(String text, int limit) {
        List<String> keywords = topKeywords(text, limit);
        List<String> stems = List.of(
                "Explain how %s is used in this context and why it matters.",
                "What trade-offs should be considered when applying %s?",
                "Describe a failure mode involving %s and how you would diagnose it.",
                "How would you demonstrate hands-on experience with %s in an interview?",
                "What is the difference between %s and the closest alternative?");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < keywords.size() && i < limit; i++) {
            result.add(String.format(stems.get(i % stems.size()), keywords.get(i)));
        }
        return result;
    }

    public static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    public static String bullets(List<String> lines) {
        return joinLines(lines.stream().map(line -> "- " + line).toList());
    }
}
