package com.aicommandcenter.ai.chat;

import com.aicommandcenter.rag.dto.AnswerSource;

import java.util.List;

public record ChatResponse(
        String reply,
        boolean grounded,
        List<AnswerSource> sources,
        String provider,
        boolean remoteProvider) {
}
