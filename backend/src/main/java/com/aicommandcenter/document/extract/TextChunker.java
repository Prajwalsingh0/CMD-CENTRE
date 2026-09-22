package com.aicommandcenter.document.extract;

import java.util.ArrayList;
import java.util.List;

/**
 * Paragraph-aware sliding-window chunker.
 *
 * <p>Naive fixed-width chunking splits sentences in half and destroys retrieval quality. This
 * version fills each chunk paragraph by paragraph and only cuts mid-paragraph when a single
 * paragraph is larger than the window, then overlaps the tail of the previous chunk so a fact
 * spanning a boundary is still retrieved.</p>
 */
public final class TextChunker {

    public static final int DEFAULT_CHUNK_CHARS = 900;
    public static final int DEFAULT_OVERLAP_CHARS = 150;

    private TextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    public static List<String> chunk(String text, int chunkChars, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int size = Math.max(200, chunkChars);
        int overlap = Math.max(0, Math.min(overlapChars, size / 2));

        List<String> paragraphs = new ArrayList<>();
        for (String block : text.replace("\r\n", "\n").split("\n{2,}")) {
            String trimmed = block.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty()) {
            paragraphs.add(text.strip());
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > size) {
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current.setLength(0);
                }
                chunks.addAll(splitLongParagraph(paragraph, size, overlap));
                continue;
            }
            if (current.length() + paragraph.length() + 2 > size && current.length() > 0) {
                String finished = current.toString().strip();
                chunks.add(finished);
                current.setLength(0);
                if (overlap > 0) {
                    current.append(tail(finished, overlap)).append("\n\n");
                }
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (current.length() > 0) {
            String last = current.toString().strip();
            if (!last.isEmpty()) {
                chunks.add(last);
            }
        }
        return chunks;
    }

    private static List<String> splitLongParagraph(String paragraph, int size, int overlap) {
        List<String> parts = new ArrayList<>();
        int step = Math.max(1, size - overlap);
        for (int start = 0; start < paragraph.length(); start += step) {
            int end = Math.min(paragraph.length(), start + size);
            String part = paragraph.substring(start, end).strip();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            if (end == paragraph.length()) {
                break;
            }
        }
        return parts;
    }

    private static String tail(String value, int length) {
        return value.length() <= length ? value : value.substring(value.length() - length);
    }
}
