package com.aicommandcenter.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeAskRequest(
        @NotBlank @Size(max = 1000) String question,
        List<Long> documentIds,
        Integer topK) {
}
