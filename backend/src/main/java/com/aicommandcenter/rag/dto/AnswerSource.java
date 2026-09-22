package com.aicommandcenter.rag.dto;

/** A retrieved passage that the answer was built from. The UI shows these as citations. */
public record AnswerSource(
        Long documentId,
        String documentName,
        int chunkIndex,
        double score,
        String excerpt) {
}
