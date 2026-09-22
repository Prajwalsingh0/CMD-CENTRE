package com.aicommandcenter.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @NotBlank @Size(max = 2000) String message,
        List<Turn> history) {

    public record Turn(@Size(max = 8000) String content) {
    }
}
