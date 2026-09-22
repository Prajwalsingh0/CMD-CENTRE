package com.aicommandcenter.document.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    @DisplayName("short text produces a single chunk")
    void shortTextIsOneChunk() {
        List<String> chunks = TextChunker.chunk("A short paragraph about indexes.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("indexes");
    }

    @Test
    @DisplayName("blank input produces nothing rather than an empty chunk")
    void blankInputProducesNothing() {
        assertThat(TextChunker.chunk("")).isEmpty();
        assertThat(TextChunker.chunk("   \n\n  ")).isEmpty();
        assertThat(TextChunker.chunk(null)).isEmpty();
    }

    @Test
    @DisplayName("paragraphs are packed up to the window and never split mid-paragraph")
    void paragraphsArePackedWhole() {
        String paragraph = "x".repeat(200);
        String text = paragraph + "\n\n" + paragraph + "\n\n" + paragraph;
        List<String> chunks = TextChunker.chunk(text, 500, 50);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(520));
    }

    @Test
    @DisplayName("consecutive chunks overlap so a fact on a boundary is still retrievable")
    void chunksOverlap() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            builder.append("Paragraph number ").append(i).append(" with a distinctive sentence of text here.\n\n");
        }
        List<String> chunks = TextChunker.chunk(builder.toString(), 300, 120);
        assertThat(chunks.size()).isGreaterThan(2);

        String firstTail = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 40));
        assertThat(chunks.get(1)).contains(firstTail.trim().substring(0, 20));
    }

    @Test
    @DisplayName("a single oversized paragraph is windowed")
    void oversizedParagraphIsSplit() {
        String monster = "word ".repeat(400).trim();
        List<String> chunks = TextChunker.chunk(monster, 400, 100);
        assertThat(chunks.size()).isGreaterThan(3);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(400));
        assertThat(String.join(" ", chunks)).contains("word word");
    }

    @Test
    @DisplayName("overlap is clamped so the chunker can never loop forever")
    void overlapIsClamped() {
        List<String> chunks = TextChunker.chunk("a ".repeat(1000), 200, 500);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isLessThan(2000);
    }
}
