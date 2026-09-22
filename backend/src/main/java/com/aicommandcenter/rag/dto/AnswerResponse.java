package com.aicommandcenter.rag.dto;

import java.util.List;

/**
 * @param grounded whether any source passage was actually retrieved — the client must be able to
 *                 tell an answer supported by documents from one that had nothing to work with
 */
public record AnswerResponse(
        String question,
        String answer,
        List<AnswerSource> sources,
        boolean grounded,
        String provider,
        boolean remoteProvider) {
}
